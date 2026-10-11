#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
数据库配置向导（setup-config.py）
================================================================
双击「1-填配置.bat」会运行本脚本。
它只做一件事：问几个问题，然后把答案写进 guard.config.json。

不用记参数、不用懂 JSON —— 看不懂的直接回车用默认值。
"""

from __future__ import annotations

import json
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
CONFIG_PATH = TOOL_DIR / "guard.config.json"

# 一份完整的默认配置，同时作为「向导保存时的字段全集」
TEMPLATE = {
    "_说明": "本文件由「1-填配置.bat」生成，只有 password / target_db 通常需要改。",
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "",
    "mysql_bin": "",
    "mysqldump_bin": "",
    "target_db": "narrative_studio",
    "project_root": "",
    "watch_interval_seconds": 15,
    "user_tables": [
        "sys_user",
        "project_member",
        "playtest_session",
        "playtest_step",
        "test_feedback",
        "player_progress",
    ],
    "optional_user_tables": [
        "project_save",
        "account_action_token",
    ],
    "candidate_exclude": [
        "information_schema",
        "mysql",
        "performance_schema",
        "sys",
    ],
}


def ask(prompt: str, default: str) -> str:
    hint = f"[回车={default}]" if default else "[回车=留空]"
    raw = input(f"{prompt} {hint}: ").strip()
    return raw or default


def main() -> int:
    cfg = dict(TEMPLATE)
    if CONFIG_PATH.exists():
        try:
            old = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
            cfg.update(old)
        except Exception:
            print("（已有配置文件读不出来，将按默认值重新生成）")

    print("=" * 58)
    print("  用户数据守护 · 数据库配置向导")
    print("=" * 58)
    print("  看不懂的直接按回车，用方括号里的默认值。")
    print()

    cfg["host"] = ask("1/5 MySQL 地址", str(cfg.get("host") or "127.0.0.1"))
    port = ask("2/5 MySQL 端口", str(cfg.get("port") or 3306))
    cfg["port"] = int(port) if port.isdigit() else 3306
    cfg["user"] = ask("3/5 MySQL 用户名", str(cfg.get("user") or "root"))
    cfg["password"] = ask("4/5 MySQL 密码", str(cfg.get("password") or ""))
    cfg["target_db"] = ask(
        "5/5 要保护的数据库名", str(cfg.get("target_db") or "narrative_studio")
    )

    print()
    print("  （下面这项目前留空就行：填了只是让检测结果里附带显示代码版本）")
    cfg["project_root"] = ask("   项目代码文件夹（可选）", str(cfg.get("project_root") or ""))

    CONFIG_PATH.write_text(
        json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print()
    print(f"✓ 已保存：{CONFIG_PATH}")
    print("  下一步：双击「2-建立保险箱.bat」。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
