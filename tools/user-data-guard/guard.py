#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
用户数据守护（user-data-guard）
================================================================
解决的问题
----------------------------------------------------------------
项目更新（换了一版代码 / 重新执行了建表脚本 / 换了一个数据库）时，
数据库里的「用户数据」——账号、项目成员关系、模拟测试记录、测试反馈、
跨局进度——很容易被新版本清空或覆盖，而且往往直到登录时才发现。

本工具做三件事：
  1) 保险箱（vault）：把当前库里的「用户数据 + 用户相关记录」抽出来存成本地快照
     （含行数与内容指纹）；
  2) 检测：每次运行都和保险箱比对，判断这次更新是否「动了用户数据」
     （账号变少 / 表被重建 / 整个库不见了）；
  3) 自动搬运：一旦检测到用户数据丢失，立刻把保险箱里的用户数据搬回目标库
     （写库前**强制先备份**目标库），只搬用户相关表，故事内容一律不动。

与其他表无关：剧情节点、选项、世界观、角色等「作品内容」不在搬运范围内。

设计原则
----------------------------------------------------------------
* 不依赖任何第三方 Python 包（只用标准库 + 本机 mysql.exe / mysqldump.exe）
* 写库前必须先备份；搬运默认 `INSERT IGNORE`（已存在的新数据优先，只补缺失行）
* 新版本结构变化（新增列）时自动做列映射，只写两边都有的列
* 所有动作都写日志：logs/ 目录

常用命令
----------------------------------------------------------------
  python guard.py init                  # 建保险箱（第一次用先跑这个）
  python guard.py check                 # 检测一次：用户数据有没有被动
  python guard.py restore               # 把保险箱的用户数据搬回目标库
  python guard.py watch                 # 常驻监控，一旦发现被清空就自动搬运
  python guard.py status                # 看当前状态摘要
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# ---------------------------------------------------------------- 路径与默认配置

TOOL_DIR = Path(__file__).resolve().parent
VAULT_DIR = TOOL_DIR / "vault"
BACKUP_DIR = VAULT_DIR / "backups"
LOG_DIR = TOOL_DIR / "logs"
CONFIG_PATH = TOOL_DIR / "guard.config.json"
VAULT_FILE = VAULT_DIR / "vault.json"
VAULT_SQL = VAULT_DIR / "user_data.sql"

DEFAULT_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    # ↓↓↓ 数据库密码：第一次用请填自己的（也可以双击「1-填配置.bat」按提示填）
    "password": "",
    # ↓↓↓ MySQL 可执行文件路径：留空 = 自动查找（先找 PATH，再扫 C:/Program Files/MySQL/*/bin/）
    "mysql_bin": "",
    "mysqldump_bin": "",
    "target_db": "narrative_studio",
    # ↓↓↓ 项目根目录：留空不影响使用，只用于在检测结果里附带显示代码版本
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
    # 监控时忽略的库（系统库 + 与本项目无关的库）
    "candidate_exclude": [
        "information_schema", "mysql", "performance_schema", "sys",
        "narrative_workshop",
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
    if CONFIG_PATH.exists():
        cfg = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        merged = dict(DEFAULT_CONFIG)
        merged.update(cfg)
        return merged
    VAULT_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(
        json.dumps(DEFAULT_CONFIG, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"已生成默认配置文件（本次按默认值继续运行）：{CONFIG_PATH}")
    if not DEFAULT_CONFIG.get("password"):
        print("⚠ 配置里的数据库密码是空的 —— 请打开 guard.config.json 填上密码后重新运行。\n")
    else:
        print("如需改数据库密码或目标库名，改完再跑一次即可。\n")
    return dict(DEFAULT_CONFIG)


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
    raise SystemExit(f"找不到 {name}，请在 guard.config.json 里填 {key} 的完整路径")


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
    # 优先按 id 排序，保证指纹可复现
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

    vault = {
        "version": 1,
        "created_at": now_str(),
        "source_db": db,
        "tables_order": [t for t in TABLE_ORDER if t in snap] or list(snap),
        "tables": {name: {k: v for k, v in info.items() if k != "rows"} for name, info in snap.items()},
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
    if missing:
        print(f"  ⚠ 这些表在库里不存在，已跳过：{', '.join(missing)}")
    print(f"  代码版本：{vault['code']['git_sha'][:8] or '未知'}（未提交文件 {vault['code']['dirty_files']} 个）")
    log(f"init 完成：来源库 {db}，{len(tables)} 表 / {total} 行", today_log())
    return 0


# ---------------------------------------------------------------- 命令：check


LEVEL_OK = "OK"
LEVEL_PARTIAL = "PARTIAL_LOSS"
LEVEL_WIPED = "WIPED"


def check_db(cfg: dict, mysql: MySql, db: str, vault: dict) -> dict:
    result = {
        "db": db,
        "db_exists": db in mysql.schemas(),
        "tables": {},
        "missing_tables": [],
        "new_columns": [],
        "lost": {},
        "level": LEVEL_OK,
        "notes": [],
    }
    if not result["db_exists"]:
        result["level"] = LEVEL_WIPED
        result["notes"].append(f"数据库 {db} 不存在（可能被删库或改成了别的库名）")
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
        result["tables"][table] = {
            "saved": saved["count"],
            "now": count_now,
            "hash": "",
        }
        if saved["count"] > 0:
            if count_now == 0:
                result["lost"][table] = saved["count"]
            elif count_now < saved["count"]:
                lost = saved["count"] - count_now
                if table == "sys_user":
                    result["lost"][table] = lost
                elif lost / saved["count"] >= 0.5:
                    result["lost"][table] = lost

    if result["missing_tables"]:
        result["level"] = LEVEL_WIPED
        result["notes"].append(
            "这些用户表在库里不见了（新版本可能重建了表结构）：" + ", ".join(result["missing_tables"]))
    elif result["lost"]:
        result["level"] = LEVEL_WIPED
        detail = "、".join(f"{t} 少 {n} 行" for t, n in result["lost"].items())
        result["notes"].append(f"用户数据比保险箱少：{detail}")
    else:
        partial = [
            f"{t}: {v['saved']} → {v['now']}"
            for t, v in result["tables"].items()
            if v["now"] is not None and 0 < v["now"] < v["saved"]
        ]
        if partial:
            result["level"] = LEVEL_PARTIAL
            result["notes"].append("略有减少（可能只是正常删了几条）：" + "；".join(partial))
    return result


def print_check(result: dict, cfg: dict, mysql: MySql) -> None:
    db = result["db"]
    icon = {LEVEL_OK: "✅", LEVEL_PARTIAL: "🟡", LEVEL_WIPED: "🔴"}[result["level"]]
    print(f"\n{icon} 检测目标库：{db}")
    if not result["db_exists"]:
        print("   数据库不存在！")
    else:
        print(f"   {'表':<20}{'保险箱':>8}{'当前':>8}   状态")
        for table, info in result["tables"].items():
            now = "—" if info["now"] is None else info["now"]
            if info["now"] is None:
                state = "表不存在"
            elif info["now"] == 0 and info["saved"] > 0:
                state = "已被清空"
            elif info["now"] < info["saved"]:
                state = "减少"
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
    db = args.db or cfg["target_db"]
    result = check_db(cfg, mysql, db, vault)

    # 代码版本对比（顺手告诉你「项目更新过」）
    cur_code = code_version(cfg)
    old_code = vault.get("code", {})
    if old_code.get("git_sha") and cur_code.get("git_sha") and \
       old_code["git_sha"] != cur_code["git_sha"]:
        print(f"\nℹ 检测到代码版本变化：{old_code['git_sha'][:8]} → {cur_code['git_sha'][:8]}")
    if old_code.get("dirty_files") != cur_code.get("dirty_files"):
        print(f"ℹ 本地未提交文件数：{old_code.get('dirty_files')} → {cur_code.get('dirty_files')}")

    print_check(result, cfg, mysql)
    log(f"check 目标库 {db}：{result['level']}", today_log())

    if args.json:
        print(json.dumps(result, ensure_ascii=False))
    if result["level"] == LEVEL_WIPED:
        print("\n👉 建议立刻执行：python guard.py restore   （把保险箱里的用户数据搬回来）")
        return 2
    if result["level"] == LEVEL_PARTIAL:
        return 3
    print("\n✅ 用户数据没有被清空。")
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


def restore_into(cfg: dict, mysql: MySql, target_db: str, vault: dict,
                 source: str = "vault", from_db: str | None = None,
                 do_backup: bool = True, mode: str = "ignore") -> dict:
    tables_saved = vault["tables"]
    order = [t for t in vault.get("tables_order", TABLE_ORDER) if t in tables_saved]
    order += [t for t in tables_saved if t not in order]

    if target_db not in mysql.schemas():
        return {"ok": False, "error": f"目标库 {target_db} 不存在；请先让新版本执行它的建库脚本，再运行搬运。"}

    backup_path = None
    if do_backup:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_path = BACKUP_DIR / f"{target_db}_搬运前_{stamp}.sql"
        mysql.dump_db(target_db, backup_path)

    live_rows = None
    if source == "live" and from_db:
        live_rows = {}
        for table in order:
            if table in set(mysql.tables(from_db)):
                rows, _ = mysql.fetch_rows(from_db, table)
                live_rows[table] = rows

    sql_parts: list[str] = ["SET NAMES utf8mb4;", "SET FOREIGN_KEY_CHECKS=0;", "START TRANSACTION;"]
    stats = {}
    warnings: list[str] = []

    if source == "live" and from_db:
        json_path = None
        rows_src = live_rows
    else:
        json_path = VAULT_DIR / "user_data.json"
        rows_src = json.loads(json_path.read_text(encoding="utf-8")) if json_path.exists() else {}

    for table in order:
        rows = (rows_src or {}).get(table) or []
        if not rows:
            stats[table] = {"planned": 0, "before": None, "after": None}
            continue
        cols_now = mysql.columns(target_db, table)
        if not cols_now:
            warnings.append(f"目标库没有表 {table}，已跳过")
            stats[table] = {"planned": 0, "before": None, "after": None, "skipped": True}
            continue

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
            warnings.append(
                f"{table}: 新版本新增了必填列 "
                + ", ".join(f"{n}（填 {v}）" for n, v in extra_cols))

        before = mysql.count(target_db, table)
        verb = "INSERT IGNORE" if mode == "ignore" else "REPLACE"
        values_lines = []
        for row in rows:
            vals = [sql_literal(row.get(c), c in base64_cols) for c in usable]
            for name, placeholder in extra_cols:
                vals[usable.index(name)] = placeholder
            values_lines.append("(" + ",".join(vals) + ")")
        col_sql = ",".join(f"`{c}`" for c in usable)
        for i in range(0, len(values_lines), 200):
            chunk = values_lines[i:i + 200]
            sql_parts.append(
                f"{verb} INTO `{table}` ({col_sql}) VALUES\n" + ",\n".join(chunk) + ";")
        stats[table] = {"planned": len(rows), "before": before, "after": None}

    sql_parts.append("COMMIT;")
    sql_parts.append("SET FOREIGN_KEY_CHECKS=1;")
    sql_text = "\n".join(sql_parts) + "\n"

    log_file = LOG_DIR / f"restore_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
    log(f"开始搬运：{source} → {target_db}", log_file)
    if backup_path:
        log(f"搬运前备份：{backup_path}", log_file)
    for w in warnings:
        log(f"警告：{w}", log_file)

    try:
        mysql.sql(sql_text, db=target_db)
    except RuntimeError as exc:
        log(f"搬运失败：{exc}", log_file)
        sql_err = LOG_DIR / "last_restore_error.sql"
        sql_err.write_text(sql_text, encoding="utf-8")
        return {"ok": False, "error": str(exc), "backup": str(backup_path),
                "sql_dump": str(sql_err)}

    for table, st in stats.items():
        if st.get("skipped") or st["planned"] == 0:
            continue
        st["after"] = mysql.count(target_db, table)

    log("搬运完成", log_file)
    for table, st in stats.items():
        if st["planned"]:
            log(f"  {table:<20} 计划 {st['planned']:>5} 行  {st['before']} → {st['after']}", log_file)
    return {"ok": True, "backup": str(backup_path) if backup_path else None,
            "stats": stats, "warnings": warnings, "log": str(log_file)}


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

    print(f"即将把用户数据搬运到：{target}")
    print(f"来源：{'数据库 ' + args.from_db if source == 'live' else '保险箱（' + vault['created_at'] + ' 建立）'}")
    print(f"涉及表：{', '.join([t for t in vault.get('tables_order', [])])}")
    print("写库前会先对目标库做完整备份。")

    result = restore_into(
        cfg, mysql, target, vault,
        source=source, from_db=args.from_db,
        do_backup=not args.no_backup, mode=args.mode,
    )
    if not result["ok"]:
        print(f"\n✗ 搬运失败：{result['error']}")
        if result.get("sql_dump"):
            print(f"  出错 SQL 已保存：{result['sql_dump']}")
        return 1

    print(f"\n✓ 搬运完成")
    if result["backup"]:
        print(f"  搬运前备份：{result['backup']}")
    for table, st in result.get("stats", {}).items():
        if st.get("planned"):
            print(f"  · {table:<20} 计划 {st['planned']:>5} 行   {st['before']} → {st['after']}")
    for w in result.get("warnings", []):
        print(f"  ⚠ {w}")
    print(f"  日志：{result['log']}")
    return 0


# ---------------------------------------------------------------- 命令：watch


def candidate_dbs(cfg: dict, mysql: MySql, only_db: str | None = None) -> list[str]:
    targets = [only_db or cfg["target_db"]]
    if not only_db and cfg.get("watch_discover", True):
        exclude = set(cfg.get("candidate_exclude", []))
        for schema in mysql.schemas():
            if schema in exclude or schema in targets:
                continue
            if "sys_user" in set(mysql.tables(schema)):
                targets.append(schema)
    return targets


def cmd_watch(cfg: dict, mysql: MySql, args) -> int:
    vault = load_vault()
    if not vault:
        print("✗ 还没有保险箱，请先运行：python guard.py init")
        return 1
    interval = args.interval or cfg["watch_interval_seconds"]
    log_file = today_log()
    log(f"开始监控（每 {interval} 秒检查一次）；保险箱建立于 {vault['created_at']}；"
        f"Ctrl+C 结束", log_file)
    last_level: dict[str, str] = {}
    rounds = 0
    while True:
        rounds += 1
        for db in candidate_dbs(cfg, mysql, args.db):
            try:
                result = check_db(cfg, mysql, db, vault)
            except Exception as exc:  # 数据库重启中之类的瞬时错误
                log(f"检查 {db} 出错（忽略本次）：{exc}", log_file)
                continue
            state = f"{db}:{result['level']}"
            if last_level.get(db) != result["level"]:
                log(f"状态变化 → {state}  {result['notes']}", log_file)
                last_level[db] = result["level"]
            if result["level"] == LEVEL_WIPED:
                log(f"🔴 检测到用户数据丢失（{db}）！立即自动搬运……", log_file)
                if args.dry_run:
                    log("（--dry-run：只报告不搬）", log_file)
                    continue
                res = restore_into(cfg, mysql, db, vault, do_backup=True, mode="ignore")
                if res["ok"]:
                    log(f"✅ 已自动把用户数据搬回 {db}；搬运前备份：{res['backup']}", log_file)
                    last_level[db] = "RESTORED"
                else:
                    log(f"✗ 自动搬运失败：{res['error']}", log_file)
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
    print(f"  代码版本：{vault['code']['git_sha'][:8] or '未知'}（未提交文件 {vault['code']['dirty_files']} 个）")
    print(f"  {'表':<20}{'行数':>7}  指纹")
    for table, info in vault["tables"].items():
        print(f"  {table:<20}{info['count']:>7}  {info['hash'] or '-'}")
    print("\n当前各库状态：")
    for db in candidate_dbs(cfg, mysql):
        result = check_db(cfg, mysql, db, vault)
        icon = {LEVEL_OK: "✅", LEVEL_PARTIAL: "🟡", LEVEL_WIPED: "🔴"}[result["level"]]
        counts = "、".join(f"{t}={v['now'] if v['now'] is not None else '缺'}"
                           for t, v in result["tables"].items())
        print(f"  {icon} {db}：{counts}")
    return 0


# ---------------------------------------------------------------- 入口


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="用户数据守护：检测更新是否清空用户数据，并自动搬运回来")
    sub = p.add_subparsers(dest="command")

    def add_db(sp):
        sp.add_argument("--db", help="目标库（默认取配置里的 target_db）")
        sp.add_argument("--include-optional", action="store_true",
                        help="把可选表（存档 project_save、找回密码令牌）也算进去")

    sp = sub.add_parser("init", help="建立保险箱（第一次使用先跑这个）")
    add_db(sp)

    sp = sub.add_parser("check", help="检测用户数据有没有被更新清掉")
    add_db(sp)
    sp.add_argument("--json", action="store_true", help="额外输出 JSON 结果")

    sp = sub.add_parser("restore", help="把用户数据搬回目标库")
    add_db(sp)
    sp.add_argument("--to-db", help="目标库（同 --db）")
    sp.add_argument("--from-db", help="直接从另一个库实时搬运（默认用保险箱）")
    sp.add_argument("--no-backup", action="store_true", help="跳过搬运前备份（不推荐）")
    sp.add_argument("--mode", choices=["ignore", "replace"], default="ignore",
                    help="ignore=新数据优先只补缺失行（默认）；replace=用保险箱覆盖同 id 的行")

    sp = sub.add_parser("watch", help="常驻监控，检测到丢失就自动搬运")
    sp.add_argument("--db", help="只监控这一个库（默认监控 target_db 并自动发现其他含 sys_user 的库）")
    sp.add_argument("--interval", type=int, help="检查间隔秒数（默认取配置）")
    sp.add_argument("--once", action="store_true", help="只检查一轮就退出")
    sp.add_argument("--dry-run", action="store_true", help="只报告，不自动搬运")

    sub.add_parser("status", help="查看保险箱与各库状态")
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
