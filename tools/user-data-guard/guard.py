#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
用户数据守护（user-data-guard）v2
================================================================
解决的问题
----------------------------------------------------------------
项目更新（换了一版代码 / 重新执行了建表脚本 / 换了一个数据库）时，
数据库里的「用户数据」——账号、项目成员关系、模拟测试记录、测试反馈、
跨局进度——很容易被新版本清空或覆盖，而且往往直到登录时才发现。

本工具做三件事：
  1) 保险箱（vault）：把当前库里的「用户数据 + 用户相关记录」抽出来存成本地快照
     （含行数与内容指纹）；
  2) 检测：每次运行都和保险箱比对，判断这次更新是否「动了用户数据」；
  3) 搬运（需人工确认）：把保险箱里的用户数据搬回**白名单内的**目标库，
     写库前强制备份、写库前先做身份与关联核验，只搬用户相关表。

与其他表无关：剧情节点、选项、世界观、角色等「作品内容」不在搬运范围内。

安全设计（v2 重点，针对代码审计意见逐条收紧）
----------------------------------------------------------------
* **只认白名单**：只检测/搬运 `target_dbs` 里明确列出的库，
  **不再自动扫描"所有含 sys_user 的库"**（避免往无关库写用户数据）。
* **告警与灾难分开**：
  - 硬信号（灾难 WIPED）：目标库不存在、用户表被重建消失、`sys_user` 被清零；
  - 软信号（告警 ALERT）：数量发生变化（正常删成员 / 清测试数据也会出现），
    只报警，**不触发恢复**。
* **身份与关联核验**：搬运前按业务键（`sys_user.username`、`narrative_project.name`）
  重建「旧 id → 新 id」映射；id 被别的对象占用、或子记录引用的项目/节点/会话
  在新库中不存在时，**默认直接中止**（`--on-conflict abort`），不会写出错绑数据。
* **不关外键检查**：默认保持 `FOREIGN_KEY_CHECKS=1`，让约束兜底；
  仅当显式加 `--disable-fk-checks` 才关闭（不推荐）。
* **搬运默认要人工确认**：非交互环境（定时任务）必须显式 `--yes`；
  常驻监控默认**只报警**，只有显式 `--auto-restore` 才会自动搬运。
* **快照必须完整**：写库前校验 `user_data.json` 是否存在、行数与指纹是否与
  `vault.json` 一致；快照缺失/损坏/为空一律判失败，不会"什么都没搬"却报成功。
* **写库后核验**：搬运后逐表比对 before/after，未达到预期即报失败。
* **凭据不入库**：真实配置写在 `guard.config.local.json`（已 gitignore），
  仓库里只保留不含密码的模板 `guard.config.json`。

设计原则
----------------------------------------------------------------
* 不依赖任何第三方 Python 包（只用标准库 + 本机 mysql.exe / mysqldump.exe）
* 写库前必须先备份；搬运默认 `INSERT IGNORE`（新数据优先，只补缺失行）
* 新版本结构变化（新增列）时自动做列映射，只写两边都有的列
* 所有动作都写日志：logs/ 目录

常用命令
----------------------------------------------------------------
  python guard.py init                  # 建保险箱（第一次用先跑这个）
  python guard.py check                 # 检测一次：用户数据有没有被动
  python guard.py check --json          # 同上，外加 JSON 输出
  python guard.py restore               # 把保险箱的用户数据搬回目标库（会先问一句）
  python guard.py restore --dry-run     # 只核验、不写库
  python guard.py watch                 # 常驻监控，默认只报警
  python guard.py watch --auto-restore  # 常驻监控，灾难时自动搬运（白名单内）
  python guard.py status                # 看当前状态摘要
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# 让中文与符号在任何控制台/管道/重定向下都能正常输出
# （Windows 非 UTF-8 代码页下打印 ✗ ✅ 🔴 等字符会直接抛异常）
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ---------------------------------------------------------------- 路径与默认配置

TOOL_DIR = Path(__file__).resolve().parent
VAULT_DIR = TOOL_DIR / "vault"
BACKUP_DIR = VAULT_DIR / "backups"
LOG_DIR = TOOL_DIR / "logs"
# 仓库里可提交的「模板」配置（不含真实密码）
CONFIG_PATH = TOOL_DIR / "guard.config.json"
# 本机真实配置（含密码，必须 gitignore）
LOCAL_CONFIG_PATH = TOOL_DIR / "guard.config.local.json"
VAULT_FILE = VAULT_DIR / "vault.json"
VAULT_SQL = VAULT_DIR / "user_data.sql"

DEFAULT_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    # 真实值在 guard.config.local.json（该文件不进仓库），或环境变量 GUARD_DB_PASSWORD。
    # 这里留空，避免把密码写死在代码里。
    "password": "",
    # 留空 = 自动查找（先找 PATH，再扫 C:/Program Files/MySQL/*/bin/）
    "mysql_bin": "",
    "mysqldump_bin": "",
    "target_db": "narrative_studio",
    # ★ 明确的目标库白名单：只检测/搬运这里列出的库。
    #   留空数组时 = 只用 target_db 一个库。绝不自动扫描其他库。
    "target_dbs": [],
    # ★ 不再自动发现「所有含 sys_user 的库」（默认关闭；即使打开，也仅用于只读检测提示）
    "watch_discover": False,
    # ★ 常驻监控默认只报警；显式改成 true（或加 --auto-restore）才会自动搬运
    "auto_restore": False,
    # ★ 冲突处置：abort=发现身份/关联冲突就中止（默认，最安全）
    #            skip=跳过冲突行与孤立记录，继续搬运其余部分
    "on_conflict": "abort",
    # 留空不影响使用，只用于在检测结果里附带显示代码版本（真实值在本地配置里）
    "project_root": "",
    "watch_interval_seconds": 15,
    # 默认搬运：用户数据 + 用户相关记录
    "user_tables": [
        "sys_user",            # 账号本体
        "project_member",      # 用户与项目的关系
        "playtest_session",    # 用户跑的模拟测试
        "playtest_step",       # 模拟测试的每一步明细
        "test_feedback",       # 用户提交的测试反馈
        "player_progress",     # 用户的跨局进度
    ],
    # 可选搬运（默认关闭，需要时加 --include-optional）
    "optional_user_tables": [
        "project_save",         # 用户创建的存档（含项目内容快照，动了会影响作品内容）
        "account_action_token", # 找回密码令牌（临时数据）
    ],
    # 仅用于「只读检测」时排除的系统库（搬运永远只认 target_dbs 白名单）
    "candidate_exclude": [
        "information_schema", "mysql", "performance_schema", "sys",
    ],
}

# 各表的搬运顺序（父表在前，避免外键指向不存在的行）
TABLE_ORDER = [
    "sys_user",
    "project_member",
    "playtest_session",
    "playtest_step",
    "test_feedback",
    "player_progress",
    "project_save",
    "account_action_token",
]

# 业务唯一键：用来跨库判断「是不是同一个对象」
BUSINESS_KEYS: dict[str, list[str]] = {
    "sys_user": ["username"],
    "narrative_project": ["name"],
    "playtest_session": ["project_id", "tester_id", "started_at"],
    "playtest_step": ["session_id", "step_no"],
    "test_feedback": ["project_id", "session_id", "title"],
    "player_progress": ["project_id", "tester_id", "version_key"],
    "project_member": ["project_id", "user_id"],
}

# 外键列 → 父表。搬运时这些列要跟着做映射 / 存在性核验
FK_PARENT: dict[str, str] = {
    "user_id": "sys_user",
    "tester_id": "sys_user",
    "reporter_id": "sys_user",
    "saved_by": "sys_user",
    "owner_id": "sys_user",
    "created_by": "sys_user",
    "project_id": "narrative_project",
    "session_id": "playtest_session",
    "step_id": "playtest_step",
    "node_id": "story_node",
    "current_node_id": "story_node",
    "choice_id": "story_choice",
    "release_id": "story_release",
}

# 本轮会被「写回」的表（这些表的 id 映射来自快照本身）
WRITTEN_TABLES = {"sys_user", "playtest_session", "playtest_step",
                  "test_feedback", "player_progress", "project_member",
                  "project_save", "account_action_token"}

# 只做存在性核验的「作品内容」表（本工具不搬它们的内容）
CONTENT_TABLES = {"narrative_project", "story_node", "story_choice", "story_release"}

BINARY_TYPES = {"blob", "tinyblob", "mediumblob", "longblob", "binary", "varbinary", "bit"}

# ---------------------------------------------------------------- 小工具


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def log(msg: str, log_file: Path | None = None, also_print: bool = True) -> None:
    line = f"[{now_str()}] {msg}"
    if also_print:
        print(line, flush=True)
    if log_file:
        log_file.parent.mkdir(parents=True, exist_ok=True)
        with log_file.open("a", encoding="utf-8") as fh:
            fh.write(line + "\n")


def today_log() -> Path:
    return LOG_DIR / f"guard_{datetime.now().strftime('%Y%m%d')}.log"


def load_config() -> dict:
    """配置优先级：guard.config.local.json > guard.config.json（模板）> 内置默认。

    密码额外支持环境变量 GUARD_DB_PASSWORD 覆盖（适合 CI / 不想落盘密码的场景）。
    """
    merged = dict(DEFAULT_CONFIG)
    template_exists = CONFIG_PATH.exists()
    local_exists = LOCAL_CONFIG_PATH.exists()

    if template_exists:
        try:
            merged.update(json.loads(CONFIG_PATH.read_text(encoding="utf-8")))
        except Exception as exc:
            print(f"⚠ 模板配置读不出来（已忽略）：{exc}")
    if local_exists:
        try:
            merged.update(json.loads(LOCAL_CONFIG_PATH.read_text(encoding="utf-8")))
        except Exception as exc:
            print(f"⚠ 本地配置读不出来（已忽略）：{exc}")

    env_pwd = os.environ.get("GUARD_DB_PASSWORD")
    if env_pwd:
        merged["password"] = env_pwd

    if not template_exists and not local_exists:
        VAULT_DIR.mkdir(parents=True, exist_ok=True)
        CONFIG_PATH.write_text(
            json.dumps(_template_config(), ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"已生成配置模板：{CONFIG_PATH}")
        print("⚠ 模板里不含密码。请运行「1-填配置」向导，或复制成 "
              f"{LOCAL_CONFIG_PATH.name} 填好后再运行。\n")

    if not merged.get("password"):
        print("⚠ 配置里的数据库密码是空的 —— 请填 guard.config.local.json "
              "（或设环境变量 GUARD_DB_PASSWORD）后重新运行。\n")
    return merged


def _template_config() -> dict:
    """返回可提交的模板配置（不含任何真实凭据与私人路径）。"""
    tpl = dict(DEFAULT_CONFIG)
    tpl["_说明"] = ("这是模板，可以提交到仓库；真实配置请写 guard.config.local.json"
                    "（该文件已被 .gitignore 忽略）。")
    return tpl


def sha16(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def file_md5(path: Path) -> str:
    h = hashlib.md5()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def find_mysql_bin(cfg: dict, key: str) -> str:
    candidate = cfg.get(key) or ""
    if candidate and Path(candidate).exists():
        return candidate
    name = "mysql.exe" if key == "mysql_bin" else "mysqldump.exe"
    found = shutil.which(name) or shutil.which(name.replace(".exe", ""))
    if found:
        return found
    for root in (Path("C:/Program Files/MySQL"), Path("C:/Program Files (x86)/MySQL")):
        if root.exists():
            hits = sorted(root.glob(f"*/bin/{name}"), reverse=True)
            if hits:
                return str(hits[0])
    raise SystemExit(f"找不到 {name}，请在 guard.config.local.json 里填 {key} 的完整路径")


# ---------------------------------------------------------------- MySQL 访问


class MySql:
    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.mysql = find_mysql_bin(cfg, "mysql_bin")
        self.dump = find_mysql_bin(cfg, "mysqldump_bin")

    def _base(self, tool: str) -> list[str]:
        return [
            tool,
            "-h", str(self.cfg["host"]),
            "-P", str(self.cfg["port"]),
            "-u", str(self.cfg["user"]),
            f"-p{self.cfg['password']}",
            "--default-character-set=utf8mb4",
        ]

    def sql(self, statements: str, db: str | None = None, raw: bool = False) -> str:
        """执行 SQL，返回 stdout 文本。"""
        args = self._base(self.mysql) + ["-N", "-B"] if raw else self._base(self.mysql)
        if db:
            args.append(db)
        proc = subprocess.run(
            args,
            input=statements.encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if proc.returncode != 0:
            err = proc.stderr.decode("utf-8", "replace").strip()
            raise RuntimeError(f"mysql 执行失败：{err}")
        return proc.stdout.decode("utf-8", "replace")

    def rows(self, statement: str, db: str | None = None) -> list[list[str]]:
        out = self.sql(statement, db=db, raw=True)
        result = []
        for line in out.splitlines():
            if line.strip() == "":
                continue
            result.append(line.split("\t"))
        return result

    def one(self, statement: str, db: str | None = None, default: str = "") -> str:
        got = self.rows(statement, db=db)
        if not got or not got[0]:
            return default
        return got[0][0]

    # ---------------- 结构信息

    def schemas(self) -> list[str]:
        return [r[0] for r in self.rows(
            "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name")]

    def tables(self, db: str) -> list[str]:
        return [r[0] for r in self.rows(
            f"SELECT table_name FROM information_schema.tables "
            f"WHERE table_schema='{db}' AND table_type='BASE TABLE' ORDER BY table_name")]

    def columns(self, db: str, table: str) -> dict[str, dict]:
        rows = self.rows(
            "SELECT column_name, data_type, is_nullable, column_default, extra "
            f"FROM information_schema.columns WHERE table_schema='{db}' "
            f"AND table_name='{table}' ORDER BY ordinal_position")
        out = {}
        for name, dtype, nullable, default, extra in rows:
            out[name] = {
                "type": dtype,
                "nullable": nullable == "YES",
                "default": None if default in ("NULL", "") else default,
                "extra": extra,
            }
        return out

    def count(self, db: str, table: str) -> int:
        got = self.one(f"SELECT COUNT(*) FROM `{db}`.`{table}`", default="-1")
        try:
            return int(got)
        except ValueError:
            return -1

    # ---------------- 取数据

    def fetch_rows(self, db: str, table: str) -> tuple[list[dict], dict[str, bool]]:
        """以 JSON 方式取出整表；二进制列自动转 base64。"""
        cols = self.columns(db, table)
        if not cols:
            return [], {}
        base64_cols: dict[str, bool] = {}
        parts = []
        for name, info in cols.items():
            if info["type"] in BINARY_TYPES:
                base64_cols[name] = True
                parts.append(f"'{name}', TO_BASE64(`{name}`)")
            else:
                parts.append(f"'{name}', `{name}`")
        expr = "JSON_OBJECT(" + ", ".join(parts) + ")"
        text = self.one(
            f"SELECT CAST(JSON_ARRAYAGG({expr}) AS CHAR) FROM `{db}`.`{table}`", default="")
        if not text or text == "NULL":
            return [], base64_cols
        data = json.loads(text)
        return data or [], base64_cols

    def dump_db(self, db: str, out_file: Path) -> Path:
        out_file.parent.mkdir(parents=True, exist_ok=True)
        args = self._base(self.dump) + [
            "--single-transaction", "--routines", "--triggers",
            "--databases", db,
        ]
        with out_file.open("wb") as fh:
            proc = subprocess.run(args, stdout=fh, stderr=subprocess.PIPE, check=False)
        if proc.returncode != 0:
            raise RuntimeError(
                f"备份失败：{proc.stderr.decode('utf-8', 'replace').strip()}")
        return out_file


# ---------------------------------------------------------------- 指纹与比对

ISO_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2}(?:\.\d+)?)$")


def normalize_value(value):
    if isinstance(value, str):
        m = ISO_RE.match(value)
        if m:
            return f"{m.group(1)} {m.group(2)}"
    return value


def canonical_rows(rows: list[dict]) -> str:
    normalized = []
    for row in rows:
        normalized.append({k: normalize_value(v) for k, v in row.items()})

    def sort_key(row):
        if "id" in row and row["id"] is not None:
            return (0, str(row["id"]))
        return (1, json.dumps(row, sort_keys=True, ensure_ascii=False, default=str))

    normalized.sort(key=sort_key)
    return json.dumps(normalized, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def table_snapshot(mysql: MySql, db: str, table: str) -> dict:
    rows, base64_cols = mysql.fetch_rows(db, table)
    cols = mysql.columns(db, table)
    return {
        "count": len(rows),
        "hash": sha16(canonical_rows(rows)) if rows else "",
        "columns": {name: info["type"] for name, info in cols.items()},
        "base64_columns": sorted(base64_cols.keys()),
        "rows": rows,
    }


def code_version(cfg: dict) -> dict:
    """记录代码版本信息，用来判断「前端/后端更新过」。"""
    root = Path(cfg.get("project_root") or "")
    info = {"git_sha": "", "dirty_files": 0, "frontend_pkg": "", "backend_pom": ""}
    if not root.exists():
        return info
    try:
        sha = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"],
                             stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
        if sha.returncode == 0:
            info["git_sha"] = sha.stdout.decode().strip()
        st = subprocess.run(["git", "-C", str(root), "status", "--porcelain"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
        if st.returncode == 0:
            info["dirty_files"] = len([x for x in st.stdout.decode().splitlines() if x.strip()])
    except Exception:
        pass
    for key, rel in (("frontend_pkg", "frontend/package.json"), ("backend_pom", "backend/pom.xml")):
        f = root / rel
        if f.exists():
            info[key] = file_md5(f)
    return info


# ---------------------------------------------------------------- 命令：init


def cmd_init(cfg: dict, mysql: MySql, args) -> int:
    db = args.db or cfg["target_db"]
    tables = list(cfg["user_tables"])
    if args.include_optional:
        tables += [t for t in cfg["optional_user_tables"] if t not in tables]

    if db not in mysql.schemas():
        print(f"✗ 数据库不存在：{db}")
        return 1

    available = set(mysql.tables(db))
    missing = [t for t in tables if t not in available]
    tables = [t for t in tables if t in available]

    VAULT_DIR.mkdir(parents=True, exist_ok=True)
    snap: dict[str, dict] = {}
    total = 0
    print(f"正在把「用户数据」抽进保险箱（来源库：{db}）……")
    for table in tables:
        info = table_snapshot(mysql, db, table)
        snap[table] = info
        total += info["count"]
        print(f"  · {table:<20} {info['count']:>5} 行  指纹 {info['hash'] or '-'}")

    # 项目「id → 名称」索引：搬运时用它把旧项目的 id 按名称映射到新库
    project_index: dict[str, str] = {}
    if "narrative_project" in available:
        proj_rows, _ = mysql.fetch_rows(db, "narrative_project")
        for r in proj_rows:
            if r.get("id") is not None:
                project_index[str(r["id"])] = str(r.get("name") or "")

    vault = {
        "version": 2,
        "created_at": now_str(),
        "source_db": db,
        "tables_order": [t for t in TABLE_ORDER if t in snap] or list(snap),
        "tables": {name: {k: v for k, v in info.items() if k != "rows"}
                   for name, info in snap.items()},
        "project_index": project_index,
        "code": code_version(cfg),
    }
    VAULT_FILE.write_text(json.dumps(vault, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "user_data.json").write_text(
        json.dumps({name: info["rows"] for name, info in snap.items()},
                   ensure_ascii=False, indent=1), encoding="utf-8")

    # 再导一份纯 SQL 备份，方便人工恢复
    if tables:
        args_dump = mysql._base(mysql.dump) + [
            "--no-create-info", "--complete-insert", "--skip-extended-insert",
            "--order-by-primary", "--skip-comments", db, *tables,
        ]
        with VAULT_SQL.open("wb") as fh:
            subprocess.run(args_dump, stdout=fh, stderr=subprocess.DEVNULL, check=False)

    print(f"\n✓ 保险箱已生成：{VAULT_FILE}")
    print(f"  用户相关表 {len(tables)} 张，合计 {total} 行")
    print(f"  项目索引 {len(project_index)} 条（用于跨库按名称对应项目）")
    if missing:
        print(f"  ⚠ 这些表在库里不存在，已跳过：{', '.join(missing)}")
    print(f"  代码版本：{vault['code']['git_sha'][:8] or '未知'}"
          f"（未提交文件 {vault['code']['dirty_files']} 个）")
    print(f"\n  受保护库（白名单）：{', '.join(whitelist_dbs(cfg))}  ← 只有这些库会被检测/搬运")
    log(f"init 完成：来源库 {db}，{len(tables)} 表 / {total} 行", today_log())
    return 0


# ---------------------------------------------------------------- 命令：check

LEVEL_OK = "OK"          # 一切正常
LEVEL_ALERT = "ALERT"    # 数量有变化 —— 只告警，不恢复
LEVEL_WIPED = "WIPED"    # 灾难：库/用户表消失、账号被清零 —— 可恢复（需确认）


def check_db(cfg: dict, mysql: MySql, db: str, vault: dict) -> dict:
    """检测单个库。硬信号 → WIPED（灾难），软信号 → ALERT（仅告警）。"""
    result = {
        "db": db,
        "db_exists": db in mysql.schemas(),
        "tables": {},
        "missing_tables": [],
        "new_columns": [],
        "lost": {},       # 硬信号：灾难级丢失
        "changed": {},    # 软信号：数量变化
        "level": LEVEL_OK,
        "notes": [],
        "hard_signals": [],
    }
    if not result["db_exists"]:
        result["level"] = LEVEL_WIPED
        result["hard_signals"].append(f"数据库 {db} 不存在（可能被删库或改了库名）")
        result["notes"].append(result["hard_signals"][-1])
        return result

    available = set(mysql.tables(db))
    for table, saved in vault["tables"].items():
        if table not in available:
            result["missing_tables"].append(table)
            result["tables"][table] = {"saved": saved["count"], "now": None, "hash": ""}
            continue
        cols_now = mysql.columns(db, table)
        new_cols = sorted(set(cols_now) - set(saved["columns"]))
        if new_cols:
            result["new_columns"].append(f"{table}: {', '.join(new_cols)}")
        count_now = mysql.count(db, table)
        result["tables"][table] = {"saved": saved["count"], "now": count_now, "hash": ""}

        if saved["count"] <= 0:
            continue
        if count_now == 0:
            if table == "sys_user":
                # 账号被清零 = 灾难
                result["lost"][table] = saved["count"]
                result["hard_signals"].append(f"{table} 被清零（{saved['count']} → 0）")
            else:
                # 其他表被清空，可能只是正常清理测试数据 → 只告警
                result["changed"][table] = (saved["count"], 0)
        elif count_now < saved["count"]:
            result["changed"][table] = (saved["count"], count_now)

    # ---- 定级
    if result["missing_tables"]:
        result["level"] = LEVEL_WIPED
        msg = ("这些用户表在新库里不存在（可能被重建）：" + "、".join(result["missing_tables"]))
        result["hard_signals"].append(msg)
        result["notes"].append(msg)
    elif result["lost"]:
        result["level"] = LEVEL_WIPED
        detail = "、".join(f"{t} 少 {n} 行" for t, n in result["lost"].items())
        result["notes"].append(f"用户数据比保险箱少：{detail}")
    elif result["changed"]:
        result["level"] = LEVEL_ALERT
        detail = "、".join(f"{t} {a} → {b}" for t, (a, b) in result["changed"].items())
        result["notes"].append(
            f"数量有变化（可能只是正常删除成员/清理测试数据）：{detail} —— 仅告警，不自动恢复")
    return result


def print_check(result: dict, cfg: dict, mysql: MySql) -> None:
    db = result["db"]
    icon = {LEVEL_OK: "✅", LEVEL_ALERT: "🟡", LEVEL_WIPED: "🔴"}[result["level"]]
    print(f"\n{icon} 检测目标库：{db}")
    if not result["db_exists"]:
        print("   数据库不存在！")
    else:
        print(f"   {'表':<20}{'保险箱':>8}{'当前':>8}   状态")
        for table, info in result["tables"].items():
            now = "—" if info["now"] is None else info["now"]
            if info["now"] is None:
                state = "表不存在（灾难）"
            elif info["now"] == 0 and info["saved"] > 0:
                state = "已被清空（灾难）" if table == "sys_user" else "已被清空（告警）"
            elif info["now"] < info["saved"]:
                state = "减少（告警）"
            elif info["now"] > info["saved"]:
                state = "比保险箱多"
            else:
                state = "正常"
            print(f"   {table:<20}{info['saved']:>8}{str(now):>8}   {state}")
    for note in result["notes"]:
        print(f"   · {note}")
    if result["new_columns"]:
        print("   · 结构已更新（新增列，搬运时会自动跳过）：" + "；".join(result["new_columns"]))


def cmd_check(cfg: dict, mysql: MySql, args) -> int:
    vault = load_vault()
    if not vault:
        print("✗ 还没有保险箱，请先运行：python guard.py init")
        return 1
    dbs = whitelist_dbs(cfg, args.db)
    print(f"受保护库（白名单）：{', '.join(dbs)}")

    cur_code = code_version(cfg)
    old_code = vault.get("code", {})
    if old_code.get("git_sha") and cur_code.get("git_sha") and \
       old_code["git_sha"] != cur_code["git_sha"]:
        print(f"ℹ 检测到代码版本变化：{old_code['git_sha'][:8]} → {cur_code['git_sha'][:8]}")

    worst = 0
    last_result = None
    for db in dbs:
        result = check_db(cfg, mysql, db, vault)
        print_check(result, cfg, mysql)
        log(f"check 目标库 {db}：{result['level']}", today_log())
        if args.json:
            print(json.dumps(result, ensure_ascii=False))
        last_result = result
        worst = max(worst, {LEVEL_OK: 0, LEVEL_ALERT: 1, LEVEL_WIPED: 2}[result["level"]])

    if worst == 2:
        print("\n🔴 检测到灾难级变化（库/用户表消失或账号被清零）。")
        print("   请人工确认后执行：python guard.py restore   （会先问一句再写库）")
        return 2
    if worst == 1:
        print("\n🟡 数量有变化，但未达到灾难级。默认不恢复、也不报错。")
        print("   若确认是误删想找回，再执行：python guard.py restore")
        return 3
    print("\n✅ 用户数据完好。")
    return 0


# ---------------------------------------------------------------- 命令：restore


def sql_literal(value, is_base64: bool = False) -> str:
    if value is None:
        return "NULL"
    if is_base64:
        text = str(value).replace("\\", "\\\\").replace("'", "''")
        return f"FROM_BASE64('{text}')"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False)
    text = str(value)
    text = normalize_value(text)
    text = text.replace("\\", "\\\\").replace("'", "''")
    return f"'{text}'"


def fill_value_for(col_info: dict) -> str | None:
    """新版本新增的 NOT NULL 且无默认值的列，给一个安全的占位值。"""
    if col_info["nullable"] or col_info["default"] is not None or "auto_increment" in col_info["extra"]:
        return None
    dtype = col_info["type"]
    if dtype in {"int", "bigint", "smallint", "tinyint", "mediumint", "decimal", "float", "double"}:
        return "0"
    if dtype in {"datetime", "timestamp"}:
        return "NOW()"
    if dtype == "date":
        return "'1970-01-01'"
    if dtype in {"json"}:
        return "'{}'"
    return "''"


def load_snapshot_rows(vault: dict) -> tuple[dict, list[str]]:
    """读取快照数据并做完整性校验；返回 (rows_by_table, problems)。"""
    problems: list[str] = []
    json_path = VAULT_DIR / "user_data.json"
    if not json_path.exists():
        return {}, [f"快照数据文件不存在：{json_path}（保险箱不完整，拒绝搬运）"]
    try:
        rows_src = json.loads(json_path.read_text(encoding="utf-8"))
    except Exception as exc:
        return {}, [f"快照数据文件无法解析：{exc}"]
    if not isinstance(rows_src, dict) or not rows_src:
        return {}, ["快照数据为空（没有任何可搬运的用户数据），拒绝搬运"]

    for table, info in vault.get("tables", {}).items():
        rows = rows_src.get(table)
        if rows is None:
            problems.append(f"{table}：快照里没有这张表的数据")
            continue
        if len(rows) != info.get("count", -1):
            problems.append(
                f"{table}：行数不符（vault.json 记录 {info.get('count')}，实际 {len(rows)}）")
            continue
        saved_hash = info.get("hash") or ""
        if saved_hash and sha16(canonical_rows(rows)) != saved_hash:
            problems.append(f"{table}：内容指纹不符（快照可能被改动或损坏）")
    return rows_src, problems


def target_business_index(mysql: MySql, db: str, table: str, cols: list[str]) -> dict[str, tuple]:
    """目标库里 id → 业务键 的索引（只读）。"""
    if table not in set(mysql.tables(db)):
        return {}
    rows, _ = mysql.fetch_rows(db, table)
    out = {}
    for r in rows:
        if r.get("id") is None:
            continue
        out[str(r["id"])] = tuple(str(r.get(c)) if r.get(c) is not None else "" for c in cols)
    return out


def target_id_set(mysql: MySql, db: str, table: str) -> set[str]:
    if table not in set(mysql.tables(db)):
        return set()
    rows, _ = mysql.fetch_rows(db, table)
    return {str(r["id"]) for r in rows if r.get("id") is not None}


def build_id_maps(mysql: MySql, target_db: str, vault: dict, rows_src: dict) -> dict:
    """建立「旧 id → 新 id」映射，并收集身份/关联冲突。

    返回 {"maps": {表: {旧id: 新id}}, "missing_parents": {...}, "conflicts": [...], "notes": [...]}
    """
    maps: dict[str, dict[str, str]] = {}
    conflicts: list[str] = []
    notes: list[str] = []

    # ---- 账号：按 username 认定「同一个人」
    snap_user = {str(r["id"]): str(r.get("username") or "")
                 for r in (rows_src.get("sys_user") or []) if r.get("id") is not None}
    tgt_user = target_business_index(mysql, target_db, "sys_user", ["username"])
    username_to_new = {}
    for nid, biz in tgt_user.items():
        username_to_new.setdefault(biz[0], nid)

    user_map: dict[str, str] = {}
    for oid, uname in snap_user.items():
        if oid in tgt_user and tgt_user[oid][0] == uname:
            user_map[oid] = oid                       # 同一个账号，id 未变
        elif uname and uname in username_to_new:
            user_map[oid] = username_to_new[uname]    # 同一个人，换了 id → 重映射
            if username_to_new[uname] != oid:
                notes.append(f"账号 {uname}：id {oid} → {username_to_new[uname]}（按用户名对应）")
        elif oid in tgt_user:
            conflicts.append(
                f"账号 id={oid} 在新库里已被「{tgt_user[oid][0]}」占用，"
                f"与快照里的「{uname}」不是同一个账号")
        else:
            user_map[oid] = oid                       # 新账号，保留原 id
    maps["sys_user"] = user_map

    # ---- 项目：按项目名对应（本工具不搬项目内容，只做 id 对应）
    project_index = {str(k): str(v) for k, v in (vault.get("project_index") or {}).items()}
    tgt_proj = target_business_index(mysql, target_db, "narrative_project", ["name"])
    name_to_new: dict[str, str] = {}
    for nid, biz in tgt_proj.items():
        name_to_new.setdefault(biz[0], nid)

    proj_map: dict[str, str] = {}
    for oid, pname in project_index.items():
        if oid in tgt_proj and tgt_proj[oid][0] == pname:
            proj_map[oid] = oid
        elif pname and pname in name_to_new:
            proj_map[oid] = name_to_new[pname]
            if name_to_new[pname] != oid:
                notes.append(f"项目「{pname}」：id {oid} → {name_to_new[pname]}（按项目名对应）")
        else:
            conflicts.append(
                f"快照里的项目「{pname}」(id={oid}) 在新库里找不到同名项目，"
                f"它的成员/测试记录无法安全落位")
    maps["narrative_project"] = proj_map

    # ---- 会话 / 步骤：id 已被占用且业务键不一致 → 冲突
    for table in ("playtest_session", "playtest_step"):
        biz_cols = BUSINESS_KEYS[table]
        snap_rows = rows_src.get(table) or []
        tgt = target_business_index(mysql, target_db, table, biz_cols)
        m: dict[str, str] = {}
        for r in snap_rows:
            if r.get("id") is None:
                continue
            oid = str(r["id"])
            # 快照里同一行可能引用了「等待映射」的 project_id/tester_id，
            # 这里先用映射前的原始值做宽松比较：若 id 不在新库 → 直接沿用。
            if oid not in tgt:
                m[oid] = oid
                continue
            # id 已被占用：只有当业务键完全一致时才认为「同一条记录」
            if _biz_matches(table, r, tgt[oid], maps):
                m[oid] = oid
            else:
                conflicts.append(f"{table} id={oid} 在新库里是另一条记录（业务键不一致）")
        maps[table] = m

    # ---- 内容表：只记录「新库里有哪些 id」用于存在性核验
    for table in CONTENT_TABLES:
        maps[table] = {}   # 占位；存在性用 id 集合判断（见 check_relations）
    return {"maps": maps, "conflicts": conflicts, "notes": notes}


def _biz_matches(table: str, snap_row: dict, target_biz: tuple, maps: dict) -> bool:
    """比较快照行与目标行的业务键（对 id 型列先按映射换算）。"""
    for i, col in enumerate(BUSINESS_KEYS[table]):
        want = snap_row.get(col)
        mapped = map_value(col, want, maps)
        got = target_biz[i] if i < len(target_biz) else ""
        if mapped is None:
            return False
        if str(mapped) != str(got):
            return False
    return True


def map_value(col: str, value, maps: dict):
    """把快照里的一个 id 值换算成目标库的 id；无法换算返回 None。"""
    parent = FK_PARENT.get(col)
    if not parent or value is None:
        return value
    m = maps.get(parent)
    if m is None:
        return value
    return m.get(str(value))


def collect_relation_problems(rows_src: dict, vault: dict, maps: dict,
                              mysql: MySql, target_db: str,
                              tables: list[str]) -> tuple[list[str], dict[str, set[int]]]:
    """逐行核验外键：映射不到或父记录在新库不存在 → 记为孤立行。

    返回 (问题描述列表, {表: 孤立行下标集合})，下标集合供 --on-conflict skip 使用。
    """
    problems: list[str] = []
    orphan_idx: dict[str, set[int]] = {}
    content_ids: dict[str, set[str]] = {}
    for table in CONTENT_TABLES:
        if table in set(mysql.tables(target_db)):
            content_ids[table] = target_id_set(mysql, target_db, table)

    for table in tables:
        rows = rows_src.get(table) or []
        hit: set[int] = set()
        for idx, r in enumerate(rows):
            for col, parent in FK_PARENT.items():
                if col not in r or r.get(col) is None:
                    continue
                val = str(r.get(col))
                if parent in WRITTEN_TABLES:
                    ok = (maps.get(parent) or {}).get(val) is not None
                else:
                    ok = val in content_ids.get(parent, set())
                if not ok:
                    hit.add(idx)
        if hit:
            orphan_idx[table] = hit
            sample = "、".join(
                f"第 {i + 1} 行" for i in sorted(hit)[:5])
            problems.append(f"{table}：{len(hit)} 条记录的关联在新库里不存在"
                            f"（{sample}{' 等' if len(hit) > 5 else ''}）")
    return problems, orphan_idx


def restore_into(cfg: dict, mysql: MySql, target_db: str, vault: dict,
                 source: str = "vault", from_db: str | None = None,
                 do_backup: bool = True, mode: str = "ignore",
                 on_conflict: str = "abort", disable_fk_checks: bool = False,
                 allow_missing_tables: bool = False,
                 dry_run: bool = False) -> dict:
    out: dict = {"ok": False, "error": "", "backup": None, "stats": {},
                 "warnings": [], "conflicts": [], "orphans": [], "notes": [],
                 "verified": False, "dry_run": dry_run}

    tables_saved = vault["tables"]
    order = [t for t in vault.get("tables_order", TABLE_ORDER) if t in tables_saved]
    order += [t for t in tables_saved if t not in order]

    if target_db not in mysql.schemas():
        out["error"] = (f"目标库 {target_db} 不存在；请先让新版本执行它的建库脚本，"
                        f"再运行搬运。")
        return out
    if target_db not in whitelist_dbs(cfg):
        out["error"] = (f"目标库 {target_db} 不在白名单 target_dbs 里，拒绝写入。"
                        f"（白名单：{', '.join(whitelist_dbs(cfg))}）")
        return out

    # ---- 1) 快照完整性（问题 5）
    if source == "live" and from_db:
        rows_src: dict = {}
        for table in order:
            if table in set(mysql.tables(from_db)):
                rows, _ = mysql.fetch_rows(from_db, table)
                rows_src[table] = rows
        if not any(rows_src.values()):
            out["error"] = f"来源库 {from_db} 里没有可搬运的用户数据，已中止。"
            return out
    else:
        rows_src, problems = load_snapshot_rows(vault)
        if problems:
            out["error"] = "保险箱快照不完整，已中止搬运：" + "；".join(problems)
            return out

    # ---- 2) 目标表是否齐全（问题 5：默认缺失即失败）
    missing = [t for t in order if t not in set(mysql.tables(target_db))]
    if missing and not allow_missing_tables:
        out["error"] = ("目标库里缺少这些用户表：" + "、".join(missing)
                        + "。默认中止（如确认要跳过，请加 --allow-missing-tables）。")
        return out

    # ---- 3) 身份 / 关联核验（问题 2）
    built = build_id_maps(mysql, target_db, vault, rows_src)
    maps = built["maps"]
    out["conflicts"] = list(built["conflicts"])
    out["notes"] = list(built["notes"])

    active_tables = [t for t in order if t not in missing]
    orphan_problems, orphan_idx = collect_relation_problems(
        rows_src, vault, maps, mysql, target_db, active_tables)
    out["orphans"] = orphan_problems

    blocking = bool(out["conflicts"] or out["orphans"])
    if blocking and on_conflict == "abort":
        detail = out["conflicts"] + out["orphans"]
        out["error"] = ("发现身份/关联冲突，已中止（未写入任何数据）：\n   - "
                        + "\n   - ".join(detail)
                        + "\n   如确认可以跳过这些记录，请用 --on-conflict skip 重试。")
        return out

    skip_conflict_users: set[str] = set()
    if out["conflicts"] and on_conflict == "skip":
        for msg in out["conflicts"]:
            m = re.search(r"id=(\d+)", msg)
            if m:
                skip_conflict_users.add(m.group(1))
    if blocking and on_conflict == "skip":
        out["warnings"].append(
            f"按 --on-conflict skip：跳过 {len(out['conflicts'])} 处身份冲突、"
            f"{sum(len(v) for v in orphan_idx.values())} 条孤立记录")

    # ---- 4) 备份
    backup_path = None
    if do_backup and not dry_run:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_path = BACKUP_DIR / f"{target_db}_搬运前_{stamp}.sql"
        mysql.dump_db(target_db, backup_path)
    out["backup"] = str(backup_path) if backup_path else None

    # ---- 5) 生成 SQL
    sql_parts: list[str] = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
    if disable_fk_checks:
        sql_parts.insert(1, "SET FOREIGN_KEY_CHECKS=0;")
        out["warnings"].append("已按参数关闭外键检查（不推荐）：孤立引用可能被写入")
    stats: dict[str, dict] = {}
    warnings = out["warnings"]

    for table in order:
        rows = rows_src.get(table) or []
        if not rows or table in missing:
            stats[table] = {"planned": 0, "before": None, "after": None,
                            "skipped": table in missing}
            continue
        cols_now = mysql.columns(target_db, table)
        saved_cols = tables_saved.get(table, {}).get("columns", {})
        base64_cols = set(tables_saved.get(table, {}).get("base64_columns", []))
        usable = [c for c in saved_cols.keys() if c in cols_now]
        extra_cols = []
        for name, info in cols_now.items():
            if name in saved_cols:
                continue
            placeholder = fill_value_for(info)
            if placeholder is not None:
                usable.append(name)
                extra_cols.append((name, placeholder))
        if extra_cols:
            warnings.append(f"{table}: 新版本新增了必填列 "
                            + ", ".join(f"{n}（填 {v}）" for n, v in extra_cols))

        before = mysql.count(target_db, table)
        verb = "INSERT IGNORE" if mode == "ignore" else "REPLACE"
        if mode == "replace":
            warnings.append("按 --mode replace：同 id 的旧行会被覆盖，请谨慎核对")

        values_lines = []
        skipped_rows = 0
        extra_names = extra_cols_names(extra_cols)
        for idx, row in enumerate(rows):
            # 跳过冲突账号 / 孤立行（on_conflict=skip 时）
            if table == "sys_user" and skip_conflict_users and str(row.get("id")) in skip_conflict_users:
                skipped_rows += 1
                continue
            if on_conflict == "skip" and idx in orphan_idx.get(table, set()):
                skipped_rows += 1
                continue
            vals = []
            for c in usable:
                if c in extra_names:
                    continue
                v = row.get(c)
                if c in FK_PARENT:
                    mapped = map_value(c, v, maps)
                    if mapped is None:
                        mapped = v     # 父表不参与映射（如内容表）时按原值写
                    v = mapped
                vals.append(sql_literal(v, c in base64_cols))
            for name, placeholder in extra_cols:
                vals.append(placeholder)
            values_lines.append("(" + ",".join(vals) + ")")

        if not values_lines:
            stats[table] = {"planned": 0, "before": before, "after": before,
                            "skipped_rows": skipped_rows}
            continue
        col_sql = ",".join(f"`{c}`" for c in usable)
        for i in range(0, len(values_lines), 200):
            chunk = values_lines[i:i + 200]
            sql_parts.append(
                f"{verb} INTO `{table}` ({col_sql}) VALUES\n" + ",\n".join(chunk) + ";")
        stats[table] = {"planned": len(values_lines), "before": before, "after": None,
                        "skipped_rows": skipped_rows}

    sql_parts.append("COMMIT;")
    if disable_fk_checks:
        sql_parts.append("SET FOREIGN_KEY_CHECKS=1;")
    sql_text = "\n".join(sql_parts) + "\n"
    out["stats"] = stats
    out["sql"] = sql_text

    if dry_run:
        out["ok"] = True
        out["verified"] = True
        out["warnings"].append("--dry-run：只核验未写入")
        return out

    # ---- 6) 执行
    log_file = LOG_DIR / f"restore_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
    log(f"开始搬运：{source} → {target_db}", log_file)
    if backup_path:
        log(f"搬运前备份：{backup_path}", log_file)
    for w in warnings:
        log(f"警告：{w}", log_file)
    out["log"] = str(log_file)

    try:
        mysql.sql(sql_text, db=target_db)
    except RuntimeError as exc:
        log(f"搬运失败：{exc}", log_file)
        sql_err = LOG_DIR / "last_restore_error.sql"
        sql_err.write_text(sql_text, encoding="utf-8")
        out["error"] = str(exc)
        out["sql_dump"] = str(sql_err)
        return out

    # ---- 7) 写后核验（问题 5）
    mismatch = []
    for table, st in stats.items():
        if st.get("skipped") or st["planned"] == 0:
            continue
        st["after"] = mysql.count(target_db, table)
        delta = (st["after"] - st["before"]) if st["before"] is not None else 0
        st["inserted"] = delta
        if delta < 0:
            mismatch.append(f"{table} 行数反而减少了（{st['before']} → {st['after']}）")
        elif delta == 0:
            mismatch.append(f"{table} 计划 {st['planned']} 行但一行都没写进去"
                            f"（可能已存在或键冲突）")
    out["mismatch"] = mismatch
    out["verified"] = not mismatch
    if mismatch:
        out["ok"] = False
        out["error"] = "搬运执行完成，但写后核验未通过：" + "；".join(mismatch)
        log(f"写后核验未通过：{mismatch}", log_file)
        return out

    log("搬运完成并通过核验", log_file)
    for table, st in stats.items():
        if st["planned"]:
            log(f"  {table:<20} 计划 {st['planned']:>5} 行  "
                f"{st['before']} → {st['after']}", log_file)
    out["ok"] = True
    return out


def extra_cols_names(extra_cols: list[tuple[str, str]]) -> set[str]:
    return {n for n, _ in extra_cols}


def load_vault() -> dict | None:
    if not VAULT_FILE.exists():
        return None
    return json.loads(VAULT_FILE.read_text(encoding="utf-8"))


def cmd_restore(cfg: dict, mysql: MySql, args) -> int:
    vault = load_vault()
    if not vault:
        print("✗ 还没有保险箱，请先运行：python guard.py init")
        return 1
    target = args.to_db or args.db or cfg["target_db"]
    source = "live" if args.from_db else "vault"
    if source == "live" and args.from_db not in mysql.schemas():
        print(f"✗ 来源库不存在：{args.from_db}")
        return 1

    on_conflict = args.on_conflict or cfg.get("on_conflict", "abort")

    print(f"即将把用户数据搬运到：{target}")
    print(f"来源：{'数据库 ' + args.from_db if source == 'live' else '保险箱（' + vault['created_at'] + ' 建立）'}")
    print(f"涉及表：{', '.join(vault.get('tables_order', []))}")
    print(f"冲突处置：{on_conflict}")
    print("写库前会先核验身份与关联、并对目标库做完整备份。")

    # 人工确认（问题 4：把「告警」与「经确认的恢复」分开）
    # 不用 isatty() 判断 —— 有些环境（管道 / 重定向 / IDE 终端）会返回 True
    # 但一读就 EOF，所以直接读、捕获异常最可靠。
    if not args.yes and not args.dry_run:
        try:
            ans = input("确认要把保险箱里的用户数据写回该库吗？[y/N] ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print("\n✗ 未获得确认（非交互环境请显式加 --yes），未写入任何数据。")
            return 1
        if ans not in ("y", "yes"):
            print("已取消，未写入任何数据。")
            return 1

    result = restore_into(
        cfg, mysql, target, vault,
        source=source, from_db=args.from_db,
        do_backup=not args.no_backup, mode=args.mode,
        on_conflict=on_conflict,
        disable_fk_checks=args.disable_fk_checks,
        allow_missing_tables=args.allow_missing_tables,
        dry_run=args.dry_run,
    )
    for note in result.get("notes", []):
        print(f"  · {note}")
    if not result["ok"]:
        print(f"\n✗ 搬运未成功：{result['error']}")
        if result.get("sql_dump"):
            print(f"  出错 SQL 已保存：{result['sql_dump']}")
        return 1

    print(f"\n✓ 搬运完成{'（dry-run，未写库）' if result.get('dry_run') else ''}，写后核验通过")
    if result["backup"]:
        print(f"  搬运前备份：{result['backup']}")
    for table, st in result.get("stats", {}).items():
        if st.get("planned"):
            print(f"  · {table:<20} 计划 {st['planned']:>5} 行   "
                  f"{st['before']} → {st['after']}")
    for w in result.get("warnings", []):
        print(f"  ⚠ {w}")
    if result.get("log"):
        print(f"  日志：{result['log']}")
    return 0


# ---------------------------------------------------------------- 命令：watch


def whitelist_dbs(cfg: dict, only_db: str | None = None) -> list[str]:
    """返回受保护库白名单（问题 1：绝不自动扫描其他库）。"""
    if only_db:
        return [only_db]
    dbs = [str(x) for x in (cfg.get("target_dbs") or [])]
    target = cfg.get("target_db")
    if target and target not in dbs:
        dbs.insert(0, target)
    return dbs


def cmd_watch(cfg: dict, mysql: MySql, args) -> int:
    vault = load_vault()
    if not vault:
        print("✗ 还没有保险箱，请先运行：python guard.py init")
        return 1
    interval = args.interval or cfg["watch_interval_seconds"]
    dbs = whitelist_dbs(cfg, args.db)
    auto = bool(args.auto_restore or cfg.get("auto_restore"))
    log_file = today_log()
    log(f"开始监控（每 {interval} 秒检查一次）；保险箱建立于 {vault['created_at']}；"
        f"白名单：{', '.join(dbs)}；模式：{'自动搬运' if auto else '只报警（不写入）'}；"
        f"Ctrl+C 结束", log_file)

    last_level: dict[str, str] = {}
    while True:
        for db in dbs:
            try:
                result = check_db(cfg, mysql, db, vault)
            except Exception as exc:  # 数据库重启中之类的瞬时错误
                log(f"检查 {db} 出错（忽略本次）：{exc}", log_file)
                continue
            state = f"{db}:{result['level']}"
            if last_level.get(db) != result["level"]:
                log(f"状态变化 → {state}  {result['notes']}", log_file)
                last_level[db] = result["level"]

            if result["level"] == LEVEL_ALERT:
                log(f"🟡 {db} 数量有变化（仅告警，不自动恢复）：{result['notes']}", log_file)
            elif result["level"] == LEVEL_WIPED:
                log(f"🔴 检测到灾难级变化（{db}）：{result['notes']}", log_file)
                if not auto:
                    log("   当前是「只报警」模式 —— 请人工确认后执行："
                        "python guard.py restore --yes", log_file)
                    continue
                if args.dry_run:
                    log("（--dry-run：只报告不搬）", log_file)
                    continue
                log(f"   开始按白名单自动搬运回 {db} ……", log_file)
                res = restore_into(cfg, mysql, db, vault, do_backup=True,
                                   mode="ignore", on_conflict=cfg.get("on_conflict", "abort"))
                if res["ok"]:
                    log(f"✅ 已自动把用户数据搬回 {db}；搬运前备份：{res['backup']}", log_file)
                    last_level[db] = "RESTORED"
                else:
                    log(f"✗ 自动搬运未执行成功（未写入脏数据）：{res['error']}", log_file)
        if args.once:
            break
        time.sleep(interval)
    return 0


# ---------------------------------------------------------------- 命令：status


def cmd_status(cfg: dict, mysql: MySql, args) -> int:
    vault = load_vault()
    if not vault:
        print("还没有保险箱：先运行 python guard.py init")
        return 1
    print(f"保险箱：{VAULT_FILE}")
    print(f"  建立时间：{vault['created_at']}    来源库：{vault['source_db']}")
    print(f"  代码版本：{vault['code']['git_sha'][:8] or '未知'}"
          f"（未提交文件 {vault['code']['dirty_files']} 个）")
    print(f"  {'表':<20}{'行数':>7}  指纹")
    for table, info in vault["tables"].items():
        print(f"  {table:<20}{info['count']:>7}  {info['hash'] or '-'}")
    print("\n受保护库（白名单）：")
    for db in whitelist_dbs(cfg):
        result = check_db(cfg, mysql, db, vault)
        icon = {LEVEL_OK: "✅", LEVEL_ALERT: "🟡", LEVEL_WIPED: "🔴"}[result["level"]]
        counts = "、".join(f"{t}={v['now'] if v['now'] is not None else '缺'}"
                           for t, v in result["tables"].items())
        print(f"  {icon} {db}：{counts}")
    print("\n（搬运永远只作用于以上白名单内的库；其他库只会在人工 --db 指定时被检查）")
    return 0


# ---------------------------------------------------------------- 入口


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="用户数据守护：检测更新是否清空用户数据，并在人工确认后搬回来")
    sub = p.add_subparsers(dest="command")

    def add_db(sp):
        sp.add_argument("--db", help="目标库（默认取白名单 target_dbs / target_db）")
        sp.add_argument("--include-optional", action="store_true",
                        help="把可选表（存档 project_save、找回密码令牌）也算进去")

    sp = sub.add_parser("init", help="建立保险箱（第一次使用先跑这个）")
    add_db(sp)

    sp = sub.add_parser("check", help="检测用户数据有没有被更新清掉")
    add_db(sp)
    sp.add_argument("--json", action="store_true", help="额外输出 JSON 结果")

    sp = sub.add_parser("restore", help="把用户数据搬回目标库（默认先问一句）")
    add_db(sp)
    sp.add_argument("--to-db", help="目标库（同 --db）")
    sp.add_argument("--from-db", help="直接从另一个库实时搬运（默认用保险箱）")
    sp.add_argument("--no-backup", action="store_true", help="跳过搬运前备份（不推荐）")
    sp.add_argument("--yes", action="store_true", help="非交互确认（定时任务用）")
    sp.add_argument("--dry-run", action="store_true", help="只核验不写库")
    sp.add_argument("--mode", choices=["ignore", "replace"], default="ignore",
                    help="ignore=新数据优先只补缺失行（默认）；replace=用保险箱覆盖同 id 的行")
    sp.add_argument("--on-conflict", choices=["abort", "skip"], default=None,
                    help="身份/关联冲突时：abort=中止（默认）；skip=跳过冲突与孤立行继续")
    sp.add_argument("--disable-fk-checks", action="store_true",
                    help="写入时关闭外键检查（不推荐，可能写入孤立引用）")
    sp.add_argument("--allow-missing-tables", action="store_true",
                    help="目标库缺表时跳过而不是中止（默认中止）")

    sp = sub.add_parser("watch", help="常驻监控（默认只报警）")
    sp.add_argument("--db", help="只监控这一个库（默认取白名单）")
    sp.add_argument("--interval", type=int, help="检查间隔秒数（默认取配置）")
    sp.add_argument("--once", action="store_true", help="只检查一轮就退出")
    sp.add_argument("--dry-run", action="store_true", help="只报告，不自动搬运")
    sp.add_argument("--auto-restore", action="store_true",
                    help="灾难时自动搬运（不推荐；默认只报警）")

    sub.add_parser("status", help="查看保险箱与白名单各库状态")
    return p


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        return 0
    cfg = load_config()
    mysql = MySql(cfg)
    handler = {
        "init": cmd_init,
        "check": cmd_check,
        "restore": cmd_restore,
        "watch": cmd_watch,
        "status": cmd_status,
    }[args.command]
    try:
        return handler(cfg, mysql, args)
    except KeyboardInterrupt:
        print("\n已停止监控。")
        return 0
    except RuntimeError as exc:
        print(f"✗ {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
