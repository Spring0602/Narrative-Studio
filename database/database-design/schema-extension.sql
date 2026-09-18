-- 叙事工坊：基于 database/schema.sql 的增量数据库设计
-- 仅在独立测试库执行一次。

ALTER TABLE sys_user
  ADD COLUMN email VARCHAR(254) NULL COMMENT '找回密码邮箱',
  ADD COLUMN email_verified_at DATETIME NULL COMMENT '邮箱验证时间',
  ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 COMMENT '登录令牌版本',
  ADD UNIQUE KEY uk_user_email (email);

CREATE TABLE account_action_token (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  purpose VARCHAR(20) NOT NULL,
  token_hash BINARY(32) NOT NULL,
  destination_email VARCHAR(254) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT uk_action_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_action_token_user
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  INDEX idx_action_token_user (user_id, purpose, used_at),
  INDEX idx_action_token_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE test_feedback
  ADD COLUMN step_id BIGINT NULL COMMENT '反馈对应的试玩步骤',
  ADD INDEX idx_feedback_step (step_id),
  ADD INDEX idx_feedback_project_status_created (project_id, status, created_at),
  ADD CONSTRAINT fk_feedback_step
    FOREIGN KEY (step_id) REFERENCES playtest_step(id) ON DELETE RESTRICT;

ALTER TABLE playtest_step
  ADD COLUMN knowledge_before JSON NULL COMMENT '执行前角色知识快照',
  ADD COLUMN knowledge_after JSON NULL COMMENT '执行后角色知识快照';

CREATE TABLE story_choice_draft (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  choice_text VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_draft_project
    FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_draft_source
    FOREIGN KEY (source_node_id) REFERENCES story_node(id) ON DELETE CASCADE,
  CONSTRAINT fk_draft_creator
    FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
  INDEX idx_draft_project_source (project_id, source_node_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_release (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  published_by BIGINT NOT NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  schema_version INT NOT NULL DEFAULT 1,
  content_snapshot JSON NOT NULL,

  CONSTRAINT uk_release_project_version UNIQUE (project_id, version_no),
  CONSTRAINT fk_release_project
    FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_release_publisher
    FOREIGN KEY (published_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
  INDEX idx_release_project_published (project_id, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE playtest_session
  ADD COLUMN release_id BIGINT NULL COMMENT '试玩使用的固定发布版本',
  ADD INDEX idx_session_release (release_id),
  ADD CONSTRAINT fk_session_release
    FOREIGN KEY (release_id) REFERENCES story_release(id) ON DELETE RESTRICT;
