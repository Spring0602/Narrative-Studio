-- Upgrade an existing 20-table database to 21 tables. Select and BACK UP the intended database first.
-- Execute once after schema-extension.sql. DDL auto-commits in MySQL; no USE and no data backfill.
ALTER TABLE state_variable ADD COLUMN persistence_scope VARCHAR(16) NOT NULL DEFAULT 'SESSION';
ALTER TABLE story_choice ADD COLUMN unlock_rule JSON NULL;
ALTER TABLE playtest_step ADD COLUMN progress_before JSON NULL, ADD COLUMN progress_after JSON NULL;
CREATE TABLE player_progress (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  tester_id BIGINT NOT NULL,
  version_key BIGINT NOT NULL DEFAULT 0 COMMENT '0=current authoring; positive=story_release.id, validated by application',
  revision BIGINT NOT NULL DEFAULT 0,
  progress_json JSON NOT NULL,
  UNIQUE KEY uk_progress_owner_version (project_id,tester_id,version_key),
  CONSTRAINT fk_progress_project FOREIGN KEY(project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_progress_tester FOREIGN KEY(tester_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Do not infer historic unlocks or change hidden_routes_unlocked globally.
-- SESSION stays the default. Authors explicitly change unlock flags to PROFILE or configure ENDING prerequisites.
