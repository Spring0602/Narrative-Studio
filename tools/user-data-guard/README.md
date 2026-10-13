# 用户数据守护 v3（2026-10-13 审查修正版）

保留成员 C 的独立工具、白名单、只读检测、配置向导、本地凭据隔离和快照校验。
修复恢复关联错误；它不是自动修库工具，也不是项目数据库迁移工具。

## 安全边界

- `check` / `watch` / `status` 只读数据库。`watch` 永远只报警，即使旧配置有 `auto_restore=true` 或传入 `--auto-restore`。
- `restore` 需人工确认（非交互显式 `--yes`），只能恢复同一 host、port、库名的 v3 快照，且目标必须在白名单。
- 不按用户名/同名项目猜测跨库 ID 映射。项目名不是唯一身份；作品内容表的完整指纹必须与快照一致，否则中止。这是保守限制，内容有正常编辑也会阻止恢复，应由人工制定迁移方案。
- 只补缺失行；已有用户必须同 ID 同用户名，保留当前密码等字段；其他已有记录与快照不同即停止，不覆盖合法新数据。
- 强制备份、保持外键、要求 InnoDB；用普通 INSERT，不用 INSERT IGNORE/REPLACE。插入错误中止批处理事务；提交后还会逐行核验。若提交后的核验失败，会明确提示事务已提交，不能声称自动回滚。
- `--no-backup`、`--mode replace`、`--on-conflict skip`、`--disable-fk-checks`、`--allow-missing-tables`、`--from-db` 均被拒绝。旧参数保留仅用于返回清楚的错误。
- 字段集合/类型必须一致；不再为新增必填列猜测 0 或空字符串。
- 检测仍以行数变化为主，行数相同不代表内容未被修改；不要把绿色状态当作完整数据库一致性证明。

## 使用顺序

需要 Python 3.10+、本机 mysql/mysqldump 客户端，推荐 MySQL 8。无需 Docker、无需第三方 Python 包。

1. 双击 `1-填配置.bat`，配置只写入被忽略的 `guard.config.local.json`。密码输入不回显。
2. 确认健康数据库、连接地址、库名，并暂停全部后端及人工写入后，双击 `2-建立保险箱.bat`。
3. 日常使用 `3-检查一次.bat` / `4-开始监控.bat`，它们只报警。
4. 发生异常后先停写，保留现场和原始快照，执行 `python guard.py restore --dry-run`。
5. 预检通过且确认确实需要恢复，再执行 `python guard.py restore`。完成后核对输出、备份以及业务功能，再恢复写入。

```powershell
python guard.py init
python guard.py check
python guard.py watch
python guard.py restore --dry-run
python guard.py restore
```

初始化及恢复需要整个期间停写：该离线工具不控制正在运行的后端，也不能阻止 Navicat 人工修改。不要与迁移、删库重建或业务操作并行运行。

## 旧保险箱与备份

v1/v2 快照缺少来源及作品内容指纹，不能自动升级为可信 v3 快照。**保留旧保险箱，不要在数据丢失后重新 init 覆盖它。** 若健康数据还在，先将整个 vault 目录归档到团队受控位置，再停写创建 v3 快照；若已丢失数据，使用原备份制定人工恢复方案。

`init` 检测到已有 vault.json 时会拒绝覆盖；缺少预期表或 SQL 导出失败也不会报告初始化成功。完整保险箱含 vault.json、user_data.json、user_data.sql。该工具不代替独立备份与恢复演练。

本地配置、vault、logs、SQL 文件均被工具目录的 .gitignore 忽略。不要用 `git add -f` 绕过保护；备份含用户数据及密码哈希，不得公开。

## 回归测试与验证范围

仓库根目录运行：

```powershell
python -B -m unittest discover -s tools/user-data-guard -p test_guard.py -v
```

测试使用临时快照与模拟数据库，覆盖旧项目 ID 被占用、同名项目、账号 ID 冲突、缺失关联、快照损坏、缺表、字段不兼容、非事务引擎、白名单、禁用危险选项、部分写入、幂等恢复、只读监控及 JSON 转义。

2026-10-13 本轮未连接真实 MySQL，未执行迁移或业务库恢复。C 在 v2 文档中记录的沙箱结果不代表 v3 已经通过真实 MySQL 验收；正式使用前仍需在隔离库验证 INSERT 失败回滚、备份恢复、JSON/二进制/时间字段和真实业务关联。
