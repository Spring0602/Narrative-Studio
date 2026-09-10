# 数据库设计增量说明

本目录基于 `database/schema.sql` 的17张基线表，新增3表、扩展4张原表。2026-09-10后端已接入这些扩展；新后端必须连接20表数据库，不能再只初始化17表。

## 新增表

| 表 | 目的 |
| --- | --- |
| `account_action_token` | 保存邮箱验证、找回密码等一次性凭据的摘要和有效期。 |
| `story_choice_draft` | 保存未指定目标节点的选项草稿，避免不完整分支进入正式剧情图。 |
| `story_release` | 保存发布时的剧情内容快照和版本号，使试玩可关联固定发布版本。 |

## 基线表扩展

| 表 | 增加内容 | 目的 |
| --- | --- | --- |
| `sys_user` | `email`、`email_verified_at`、`token_version` | 支持邮箱找回、邮箱验证和旧令牌失效。 |
| `test_feedback` | `step_id` 与索引 | 让反馈准确定位到某一步试玩记录。 |
| `playtest_step` | `knowledge_before`、`knowledge_after` | 保存角色知识在每步前后的快照。 |
| `playtest_session` | `release_id` 与索引 | 标识该会话使用的固定发布版本。 |

## 使用方式

1. 执行前先备份；MySQL的DDL不能依靠事务回滚。由C维护SQL并在独立测试库验证，B审查设计/迁移安全、A确认后端兼容后，再由C安排业务库升级；本轮开发未执行实际数据库DDL。
2. 复制 `database/schema.sql`，将副本中的 `CREATE DATABASE` 和 `USE` 数据库名称均改为专用的独立测试库名称，再执行副本。原脚本固定使用 `narrative_studio`，不能直接用来初始化隔离测试库。
3. 在 Navicat 中确认当前连接、查询窗口所选数据库均为该独立测试库，显式执行对应的 `USE` 后，再执行本目录的 `schema-extension.sql` 一次；该扩展不支持重复执行。
4. 如果已有20表测试库，不要重复执行扩展。先运行只读 `verify-extension.sql`，核对当前数据库名称、20张预期表及9个核对列（原表7个扩展列 + 发布表2个快照列）；再核查数据类型、索引与外键。
5. 仅17表的已有库不需要重建基线；备份和隔离验证后，在明确选中的目标库执行扩展一次。部分执行失败时停止，不要盲目重跑；对照表结构定位已经成功的语句。
6. 为启动进程设置DB_URL/DB_USERNAME/DB_PASSWORD，指向已验证库。启动时只读检查扩展列，不自动迁移。测试镜像 `backend/src/test/resources/database-extension-test.sql` 只供H2使用，不可用于MySQL部署。

后端已实现邮箱凭据、草稿转正、发布快照及版本试玩；真实MySQL、SMTP和前端页面尚待联调。接口、事务和部署边界见 [数据库接口补全说明](../../docs/12-数据库接口补全与联调说明.md)。
