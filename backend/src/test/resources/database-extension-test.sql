-- Test-only extension mirror; production uses MySQL JSON, not CLOB.
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS email VARCHAR(254) NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS email_verified_at DATETIME NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON sys_user(email);
CREATE TABLE IF NOT EXISTS account_action_token (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, purpose VARCHAR(20) NOT NULL,
  token_hash BINARY(32) NOT NULL UNIQUE, destination_email VARCHAR(254) NOT NULL,
  expires_at DATETIME NOT NULL, used_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS story_choice_draft (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL, source_node_id BIGINT NOT NULL,
  choice_text VARCHAR(500) NULL, sort_order INT NOT NULL DEFAULT 0, created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  FOREIGN KEY(source_node_id) REFERENCES story_node(id) ON DELETE CASCADE,
  FOREIGN KEY(created_by) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS story_release (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL, version_no INT NOT NULL,
  published_by BIGINT NOT NULL, published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  schema_version INT NOT NULL DEFAULT 1, content_snapshot CLOB NOT NULL,
  UNIQUE(project_id,version_no),
  FOREIGN KEY(project_id) REFERENCES narrative_project(id),
  FOREIGN KEY(published_by) REFERENCES sys_user(id)
);
ALTER TABLE playtest_session ADD COLUMN IF NOT EXISTS release_id BIGINT NULL REFERENCES story_release(id);
ALTER TABLE playtest_step ADD COLUMN IF NOT EXISTS knowledge_before CLOB NULL;
ALTER TABLE playtest_step ADD COLUMN IF NOT EXISTS knowledge_after CLOB NULL;
ALTER TABLE test_feedback ADD COLUMN IF NOT EXISTS step_id BIGINT NULL REFERENCES playtest_step(id);
ALTER TABLE state_variable ADD COLUMN IF NOT EXISTS persistence_scope VARCHAR(16) NOT NULL DEFAULT 'SESSION';
ALTER TABLE story_choice ADD COLUMN IF NOT EXISTS unlock_rule CLOB NULL;
ALTER TABLE playtest_step ADD COLUMN IF NOT EXISTS progress_before CLOB NULL;
ALTER TABLE playtest_step ADD COLUMN IF NOT EXISTS progress_after CLOB NULL;
CREATE TABLE IF NOT EXISTS player_progress (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL, tester_id BIGINT NOT NULL,
  version_key BIGINT NOT NULL DEFAULT 0, revision BIGINT NOT NULL DEFAULT 0, progress_json CLOB NOT NULL,
  UNIQUE(project_id,tester_id,version_key),
  FOREIGN KEY(project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  FOREIGN KEY(tester_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
