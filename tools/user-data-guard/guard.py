#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""用户数据守护 v3：只读检测与人工确认的同库缺失行恢复。

保留 C 的白名单、配置隔离与完整性检查；恢复由 safe_recovery 实现。
不支持跨库 ID 猜测映射，不覆盖已有行，不关闭外键，不自动恢复。
init/restore 必须在停写维护窗口操作；旧快照保留，不在数据丢失后重建。
完整使用边界与离线测试命令见 README.md。
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
    # 常驻监控仅报警；旧 auto_restore 配置不再启用自动写入
    "auto_restore": False,
    # 冲突直接中止，旧 skip 参数只保留用于返回明确错误
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

# 作品内容指纹核验范围（只读，不恢复这些表）
CONTENT_TABLES = {
    "narrative_project", "story_node", "story_choice", "story_release",
    "world_entry", "character_profile", "character_relation", "character_knowledge",
    "node_character", "state_variable", "choice_condition", "state_effect", "story_choice_draft",
}

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
            raise RuntimeError(f"模板配置损坏，已停止：{exc}") from exc
    if local_exists:
        try:
            merged.update(json.loads(LOCAL_CONFIG_PATH.read_text(encoding="utf-8")))
        except Exception as exc:
            raise RuntimeError(f"本地配置损坏，已停止：{exc}") from exc

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
        args = self._base(self.mysql) + ["-N", "-B", "--raw"] if raw else self._base(self.mysql)
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
    if missing:
        raise RuntimeError(f"Snapshot tables missing: {missing}")
    tables = [t for t in tables if t in available]

    if VAULT_FILE.exists():
        raise RuntimeError("Archive the existing vault before init; never overwrite a recovery snapshot after data loss.")
    VAULT_DIR.mkdir(parents=True, exist_ok=True)
    snap: dict[str, dict] = {}
    total = 0
    print(f"正在把「用户数据」抽进保险箱（来源库：{db}）……")
    for table in tables:
        info = table_snapshot(mysql, db, table)
        snap[table] = info
        total += info["count"]
        print(f"  · {table:<20} {info['count']:>5} 行  指纹 {info['hash'] or '-'}")

    # 项目名称仅作为人工审查索引；恢复不使用名称映射 ID
    project_index: dict[str, str] = {}
    if "narrative_project" in available:
        proj_rows, _ = mysql.fetch_rows(db, "narrative_project")
        for r in proj_rows:
            if r.get("id") is not None:
                project_index[str(r["id"])] = str(r.get("name") or "")

    from safe_recovery import content_manifest
    vault = {
        "version": 3,
        "source": {"host": str(cfg["host"]), "port": int(cfg["port"]), "database": db},
        "content_manifest": content_manifest(sys.modules[__name__], mysql, db),
        "created_at": now_str(),
        "source_db": db,
        "tables_order": [t for t in TABLE_ORDER if t in snap] or list(snap),
        "tables": {name: {k: v for k, v in info.items() if k != "rows"}
                   for name, info in snap.items()},
        "project_index": project_index,
        "code": code_version(cfg),
    }
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
            result = subprocess.run(args_dump, stdout=fh, stderr=subprocess.PIPE, check=False)
        if result.returncode:
            raise RuntimeError("SQL backup export failed; vault initialization incomplete.")

    VAULT_FILE.write_text(json.dumps(vault, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n✓ 保险箱已生成：{VAULT_FILE}")
    print(f"  用户相关表 {len(tables)} 张，合计 {total} 行")
    print(f"  项目索引 {len(project_index)} 条；已记录作品内容指纹，不按名称映射 ID")
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
        if rows and not saved_hash:
            problems.append(f"{table}：非空快照缺少指纹")
        if saved_hash and sha16(canonical_rows(rows)) != saved_hash:
            problems.append(f"{table}：内容指纹不符（快照可能被改动或损坏）")
    return rows_src, problems


def restore_into(cfg, mysql, target_db, vault, **kwargs):
    from safe_recovery import restore
    return restore(sys.modules[__name__], cfg, mysql, target_db, vault, **kwargs)


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

    print("\n✓ 预检通过，未写库" if result.get("dry_run") else "\n✓ 恢复核验通过")
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
    auto = False
    if args.auto_restore or cfg.get("auto_restore"):
        print("Auto-restore disabled: stop writers and run manual restore after review.")
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
                    log(f"✗ 恢复未成功，请核查结果与备份：{res['error']}", log_file)
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
