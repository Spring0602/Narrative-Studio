# 数据库设计增量说明

本目录基于 `database/schema.sql` 的 17 张基线表，记录需求分析补充的数据库设计。它不修改现有业务代码。

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

1. 先执行原有的 `database/schema.sql`。
2. 仅在独立测试库执行本目录的 `schema-extension.sql` 一次。
3. 执行前先备份；MySQL 的 DDL 不能依靠事务回滚。

数据库结构只负责保存数据。邮箱发送、找回密码、草稿转正式选项、发布快照生成，以及试玩按 `release_id` 读取版本，仍需后端和前端接入。
