-- 21 -> 22 tables. Back up and select the intended MySQL database explicitly. Execute once.
-- MANUAL rows: newest five per project. AUTO slot: one per project/user, independent of manual history.
CREATE TABLE project_save (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL,
 saved_by BIGINT NOT NULL,
 save_kind VARCHAR(16) NOT NULL,
 auto_slot BIGINT NULL COMMENT 'user id for AUTO, NULL for MANUAL',
 revision BIGINT NOT NULL DEFAULT 1,
 label VARCHAR(100) NOT NULL,
 content_hash CHAR(64) NOT NULL,
 content_snapshot JSON NOT NULL,
 saved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_project_auto_slot(project_id,auto_slot),
 INDEX idx_project_save_history(project_id,save_kind,id),
 CONSTRAINT fk_save_project FOREIGN KEY(project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
 CONSTRAINT fk_save_user FOREIGN KEY(saved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
