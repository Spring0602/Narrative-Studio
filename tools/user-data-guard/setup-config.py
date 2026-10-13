#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
数据库配置向导（setup-config.py）
================================================================
双击「1-填配置.bat」会运行本脚本。
它只做一件事：问几个问题，然后把答案写进 **guard.config.local.json**。

为什么写进 local 文件而不是 guard.config.json？
------------------------------------------------
guard.config.json 是「模板」，里面不含密码、可以随便提交到 Git 仓库；
guard.config.local.json 是「你本机的真实配置」（含数据库密码），
它已经被 .gitignore 忽略，**不会被提交**，也不应该外发。

不用记参数、不用懂 JSON —— 看不懂的直接回车用默认值。
"""

from __future__ import annotations

import json
import getpass
import sys
from pathlib import Path

# 让中文与符号在任何控制台/管道/重定向下都能正常输出
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

TOOL_DIR = Path(__file__).resolve().parent
TEMPLATE_PATH = TOOL_DIR / "guard.config.json"        # 模板（只读，可提交）
LOCAL_PATH = TOOL_DIR / "guard.config.local.json"     # 本机真实配置（不提交）

# 向导保存时的字段全集（与 guard.py 的 DEFAULT_CONFIG 对齐）
DEFAULTS = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "",
    "mysql_bin": "",
    "mysqldump_bin": "",
    "target_db": "narrative_studio",
    "target_dbs": [],
    "watch_discover": False,
    "auto_restore": False,
    "on_conflict": "abort",
    "project_root": "",
    "watch_interval_seconds": 15,
}


def ask(prompt: str, default: str) -> str:
    hint = f"[回车={default}]" if default else "[回车=留空]"
    raw = input(f"{prompt} {hint}: ").strip()
    return raw or default


def load_existing() -> dict:
    cfg = dict(DEFAULTS)
    for path in (TEMPLATE_PATH, LOCAL_PATH):   # 模板先、本机配置后（后者覆盖）
        if path.exists():
            try:
                cfg.update(json.loads(path.read_text(encoding="utf-8")))
            except Exception:
                pass
    return cfg


def main() -> int:
    cfg = load_existing()

    print("=" * 62)
    print("  用户数据守护 · 数据库配置向导")
    print("=" * 62)
    print("  看不懂的直接按回车，用方括号里的默认值。")
    print(f"  答案只会写进本机的 {LOCAL_PATH.name}（不会被提交到 Git 仓库）。")
    print()

    cfg["host"] = ask("1/6 MySQL 地址", str(cfg.get("host") or "127.0.0.1"))
    port = ask("2/6 MySQL 端口", str(cfg.get("port") or 3306))
    cfg["port"] = int(port) if port.isdigit() else 3306
    cfg["user"] = ask("3/6 MySQL 用户名", str(cfg.get("user") or "root"))
    cfg["password"] = getpass.getpass("4/6 MySQL 密码（不回显，回车保留已有值）: ") or cfg.get("password", "")
    target = ask("5/6 要保护的数据库名", str(cfg.get("target_db") or "narrative_studio"))
    cfg["target_db"] = target
    # 白名单：默认只保护这一个库（安全默认，不自动扫描其他库）
    cfg["target_dbs"] = [target]

    print()
    print("  （下面这项目前留空就行：填了只是让检测结果里附带显示代码版本）")
    cfg["project_root"] = ask("6/6 项目代码文件夹（可选）", str(cfg.get("project_root") or ""))

    cfg["_说明"] = ("本机真实配置（含数据库密码），由「1-填配置.bat」生成；"
                    "已被 .gitignore 忽略，不要提交、不要外发。")

    LOCAL_PATH.write_text(
        json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print()
    print(f"✓ 已保存：{LOCAL_PATH.name}")
    print(f"  受保护库白名单：{', '.join(cfg['target_dbs'])}")
    print("  注意：只有白名单里的库会被检测/搬运，不会碰其他数据库。")
    print("  （也可以不落盘密码：设环境变量 GUARD_DB_PASSWORD 即可覆盖此项）")
    print("  下一步：双击「2-建立保险箱.bat」。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
