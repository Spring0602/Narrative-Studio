# 数据库只读核验记录（成员 B 复验）

## 1. 核验结论

- 核验日期：2026-10-05
- 核验人员：成员 B（卢佳妮）
- 核验数据库：`narrative_studio`
- 核验工具：MySQL Workbench
- 核验脚本：`verify-extension.sql`、`verify-progression.sql`、`verify-history.sql`
- 核验方式：在本机 MySQL 8 数据库中执行只读查询，逐项检查表、字段、索引、外键和异常数据
- 最终结果：**全部通过**
- 证据数量：17 张截图

本记录属于成员 B 的独立复验材料，用于向成员 C 提供可汇总的执行证据；成员 C 仍应按分工完成正式业务数据库迁移、备份恢复及最终交付汇总。

## 2. 核验环境

| 项目 | 内容 |
| --- | --- |
| 操作系统 | Windows 11 |
| 数据库管理工具 | MySQL Workbench 8.0.47 |
| 数据库服务器 | MySQL Server 8.0.46 |
| 连接 | `Local instance MySQL80` / `localhost:3306` |
| 目标数据库 | `narrative_studio` |
| 基础结构脚本 | `database/schema.sql` |
| 结构扩展脚本 | `schema-extension.sql`、`progression-extension.sql`、`history-extension.sql` |
| 只读核验脚本 | `verify-extension.sql`、`verify-progression.sql`、`verify-history.sql` |

说明：三个核验脚本只读取数据库元数据和业务数据；`verify-extension.sql` 中的 `SET @verify_project_id = 1` 仅设置当前会话变量，不修改持久化数据。

## 3. 核验结果汇总

| 脚本 | 核验内容 | 预期 | 实际 | 结果 |
| --- | --- | --- | --- | --- |
| `verify-extension.sql` | 指定基础表与第一阶段扩展表 | 20 张 | 20 张 | 通过 |
| `verify-extension.sql` | 第一阶段扩展字段 | 9 项 | 9 项 | 通过 |
| `verify-progression.sql` | 跨局进度相关表 | 21 张 | 21 张 | 通过 |
| `verify-progression.sql` | 跨局进度扩展字段 | 4 项 | 4 项 | 通过 |
| `verify-progression.sql` | `player_progress` 主键、唯一键、索引和外键 | 均存在 | 均存在 | 通过 |
| `verify-progression.sql` | 非法持久化范围 | 0 | 0 | 通过 |
| `verify-progression.sql` | 非法玩家进度 | 0 | 0 | 通过 |
| `verify-progression.sql` | 孤立发布版本引用 | 0 | 0 | 通过 |
| `verify-progression.sql` | 重复玩家进度组合 | 无结果行 | 无结果行 | 通过 |
| `verify-history.sql` | `project_save` 字段 | 10 项 | 10 项 | 通过 |
| `verify-history.sql` | `project_save` 索引 | 结构符合设计 | 结构符合设计 | 通过 |
| `verify-history.sql` | `project_save` 外键 | 2 项 | 2 项 | 通过 |
| `verify-history.sql` | 手动版本超过 5 份 | 无结果行 | 无结果行 | 通过 |
| `verify-history.sql` | 自动保存槽位重复 | 无结果行 | 无结果行 | 通过 |
| `verify-history.sql` | 非法历史保存记录 | 无结果行 | 无结果行 | 通过 |

## 4. `verify-extension.sql` 核验记录

### 4.1 指定基础表数量

查询针对脚本列出的 20 张指定表进行计数，而不是任意统计数据库中的表总数。实际返回 `expected_tables_present = 20`，与预期一致。

![基础20表核验](<./验收截图/数据库只读核验截图/B-SQL复验-01-基础20表核验.png>)

### 4.2 第一阶段扩展字段

实际返回 9 项扩展字段，覆盖 `sys_user`、`test_feedback`、`playtest_step`、`playtest_session` 和 `story_release`；字段类型、可空性及默认值均可见，数量与脚本预期一致。

![扩展字段9项核验](<./验收截图/数据库只读核验截图/B-SQL复验-02-扩展字段9项核验.png>)

## 5. `verify-progression.sql` 核验记录

### 5.1 跨局进度相关表与扩展字段

脚本指定的相关表实际存在 21 张；跨局进度扩展字段实际返回 4 项，分别为 `playtest_step.progress_before`、`playtest_step.progress_after`、`state_variable.persistence_scope` 和 `story_choice.unlock_rule`。

![跨局进度21表核验](<./验收截图/数据库只读核验截图/B-SQL复验-03-跨局进度21表核验.png>)

![跨局进度4个扩展字段](<./验收截图/数据库只读核验截图/B-SQL复验-04-跨局进度4个扩展字段.png>)

### 5.2 `player_progress` 约束与索引

`player_progress` 具备主键、项目外键、测试者外键，以及由 `project_id + tester_id + version_key` 组成的唯一约束。索引查询中该组合的 `NON_UNIQUE = 0`，确认其唯一性。

![player_progress主键唯一键与外键](<./验收截图/数据库只读核验截图/B-SQL复验-05-player_progress主键唯一键与外键.png>)

![player_progress索引核验](<./验收截图/数据库只读核验截图/B-SQL复验-06-player_progress索引核验.png>)

### 5.3 跨局进度异常数据检查

- 非法持久化范围数量为 0。
- 非法玩家进度数量为 0。
- 孤立发布版本引用数量为 0。
- 重复的 `project_id + tester_id + version_key` 组合查询无结果行。

![非法持久化范围为0](<./验收截图/数据库只读核验截图/B-SQL复验-07A-非法持久化范围为0.png>)

![非法玩家进度为0](<./验收截图/数据库只读核验截图/B-SQL复验-07B-非法玩家进度为0.png>)

![孤立发布版本为0](<./验收截图/数据库只读核验截图/B-SQL复验-07C-孤立发布版本为0.png>)

![玩家进度无重复记录](<./验收截图/数据库只读核验截图/B-SQL复验-07D-玩家进度无重复记录.png>)

## 6. `verify-history.sql` 核验记录

### 6.1 脚本整体执行状态

脚本中的字段、索引、外键和异常数据查询全部执行成功，Action Output 中无错误；字段查询返回 10 行、索引查询返回 7 行、外键查询返回 2 行，三项异常查询均返回 0 行。

![历史核验脚本全部执行成功](<./验收截图/数据库只读核验截图/B-SQL复验-08-历史核验脚本全部执行成功.png>)

### 6.2 `project_save` 字段

`project_save` 实际包含 10 个字段：`id`、`project_id`、`saved_by`、`save_kind`、`auto_slot`、`revision`、`label`、`content_hash`、`content_snapshot`、`saved_at`。其中 `revision` 默认值为 1，`content_hash` 为 `char(64)`，`content_snapshot` 为 `json`，`saved_at` 默认使用当前时间。

![project_save的10个字段](<./验收截图/数据库只读核验截图/B-SQL复验-09-project_save的10个字段.png>)

### 6.3 `project_save` 索引与外键

索引结构包含：

- 主键 `PRIMARY(id)`；
- 历史查询索引 `idx_project_save_history(project_id, save_kind, id)`；
- 自动槽位唯一索引 `uk_project_auto_slot(project_id, auto_slot)`；
- 保存用户辅助索引 `fk_save_user(saved_by)`。

外键结构包含：

- `project_id → narrative_project.id`；
- `saved_by → sys_user.id`。

![project_save索引核验](<./验收截图/数据库只读核验截图/B-SQL复验-10-project_save索引核验.png>)

![project_save两个外键](<./验收截图/数据库只读核验截图/B-SQL复验-11-project_save两个外键.png>)

### 6.4 历史保存异常数据检查

- “手动版本超过 5 份”查询无结果行。
- “同一项目与自动槽位存在多条自动保存”查询无结果行。
- 修订号、哈希长度、JSON、保存类型及自动槽位规则异常查询无结果行。

最后一项截图中的灰色 `NULL` 行左侧带星号，是 MySQL Workbench 的可编辑新增空白行，不属于查询返回的数据；Action Output 显示该查询返回 0 行。

![手动历史版本未超过5个](<./验收截图/数据库只读核验截图/B-SQL复验-12A-手动历史版本未超过5个.png>)

![自动保存槽位无重复](<./验收截图/数据库只读核验截图/B-SQL复验-12B-自动保存槽位无重复.png>)

![project_save无非法记录](<./验收截图/数据库只读核验截图/B-SQL复验-12C-project_save无非法记录.png>)

## 7. 最终判定与交接说明

三个只读核验脚本均已在目标数据库 `narrative_studio` 上执行完成。指定表和扩展字段数量符合预期，`player_progress` 与 `project_save` 的关键字段、索引及外键结构符合设计，脚本定义的异常数据检查均为 0 或无结果行。

**成员 B 复验结论：通过。**

交接给成员 C 时应同时提供：

1. 本 Markdown 记录；
2. `docs/验收截图/数据库只读核验截图` 中的 17 张原始截图；
3. 三个原始只读核验脚本；
4. 说明本记录是本地开发数据库复验，正式业务数据库的迁移、备份恢复和最终汇总仍由成员 C 负责。
