-- Navicat 执行入口 1/2：先运行本文件创建 MySQL 数据库和全部业务表。
-- 适用版本：MySQL 8.0+。执行完成后可继续运行 demo_story.sql。

CREATE DATABASE IF NOT EXISTS narrative_studio
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE narrative_studio;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS narrative_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000),
  owner_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
  INDEX idx_project_owner (owner_id),
  INDEX idx_project_status_updated (status, updated_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS project_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(20) NOT NULL,
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_project_user UNIQUE (project_id, user_id),
  CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  INDEX idx_member_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS world_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  entry_type VARCHAR(30) NOT NULL,
  title VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_world_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  INDEX idx_world_project_type (project_id, entry_type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS character_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  summary VARCHAR(500),
  personality TEXT,
  goal TEXT,
  value_order TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_character_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  INDEX idx_character_project (project_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS character_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  source_character_id BIGINT NOT NULL,
  target_character_id BIGINT NOT NULL,
  relation_type VARCHAR(40) NOT NULL,
  description VARCHAR(1000),
  CONSTRAINT uk_character_relation UNIQUE (source_character_id, target_character_id, relation_type),
  CONSTRAINT fk_relation_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_relation_source FOREIGN KEY (source_character_id) REFERENCES character_profile(id) ON DELETE CASCADE,
  CONSTRAINT fk_relation_target FOREIGN KEY (target_character_id) REFERENCES character_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS story_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  node_key VARCHAR(64) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content TEXT,
  node_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  scene VARCHAR(100),
  is_start TINYINT(1) NOT NULL DEFAULT 0,
  position_x DECIMAL(10,2) NOT NULL DEFAULT 0,
  position_y DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_project_node_key UNIQUE (project_id, node_key),
  CONSTRAINT fk_node_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  INDEX idx_node_project_type (project_id, node_type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS character_knowledge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  character_id BIGINT NOT NULL,
  knowledge_key VARCHAR(100) NOT NULL,
  knowledge_level VARCHAR(20) NOT NULL DEFAULT 'KNOWN',
  description VARCHAR(1000),
  acquired_node_id BIGINT NULL,
  CONSTRAINT uk_character_knowledge UNIQUE (character_id, knowledge_key),
  CONSTRAINT fk_knowledge_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_character FOREIGN KEY (character_id) REFERENCES character_profile(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_node FOREIGN KEY (acquired_node_id) REFERENCES story_node(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS node_character (
  node_id BIGINT NOT NULL,
  character_id BIGINT NOT NULL,
  PRIMARY KEY (node_id, character_id),
  CONSTRAINT fk_node_character_node FOREIGN KEY (node_id) REFERENCES story_node(id) ON DELETE CASCADE,
  CONSTRAINT fk_node_character_character FOREIGN KEY (character_id) REFERENCES character_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS story_choice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  target_node_id BIGINT NOT NULL,
  choice_text VARCHAR(500) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_choice_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_choice_source FOREIGN KEY (source_node_id) REFERENCES story_node(id) ON DELETE CASCADE,
  CONSTRAINT fk_choice_target FOREIGN KEY (target_node_id) REFERENCES story_node(id) ON DELETE RESTRICT,
  INDEX idx_choice_source (source_node_id, sort_order),
  INDEX idx_choice_target (target_node_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS state_variable (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  variable_key VARCHAR(64) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  value_type VARCHAR(20) NOT NULL,
  initial_value VARCHAR(500) NOT NULL,
  description VARCHAR(500),
  CONSTRAINT uk_project_variable_key UNIQUE (project_id, variable_key),
  CONSTRAINT fk_variable_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS choice_condition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  choice_id BIGINT NOT NULL,
  variable_id BIGINT NOT NULL,
  operator VARCHAR(20) NOT NULL,
  expected_value VARCHAR(500) NOT NULL,
  condition_group INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_condition_choice FOREIGN KEY (choice_id) REFERENCES story_choice(id) ON DELETE CASCADE,
  CONSTRAINT fk_condition_variable FOREIGN KEY (variable_id) REFERENCES state_variable(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS state_effect (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  choice_id BIGINT NOT NULL,
  variable_id BIGINT NOT NULL,
  operation VARCHAR(20) NOT NULL,
  operand_value VARCHAR(500) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_effect_choice FOREIGN KEY (choice_id) REFERENCES story_choice(id) ON DELETE CASCADE,
  CONSTRAINT fk_effect_variable FOREIGN KEY (variable_id) REFERENCES state_variable(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS playtest_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  tester_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  current_node_id BIGINT NOT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  CONSTRAINT fk_session_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_session_tester FOREIGN KEY (tester_id) REFERENCES sys_user(id),
  CONSTRAINT fk_session_current_node FOREIGN KEY (current_node_id) REFERENCES story_node(id),
  INDEX idx_session_project_started (project_id, started_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS playtest_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  step_no INT NOT NULL,
  node_id BIGINT NOT NULL,
  choice_id BIGINT NULL,
  state_before JSON NOT NULL,
  state_after JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_session_step UNIQUE (session_id, step_no),
  CONSTRAINT fk_step_session FOREIGN KEY (session_id) REFERENCES playtest_session(id) ON DELETE CASCADE,
  CONSTRAINT fk_step_node FOREIGN KEY (node_id) REFERENCES story_node(id),
  CONSTRAINT fk_step_choice FOREIGN KEY (choice_id) REFERENCES story_choice(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS detected_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  issue_type VARCHAR(40) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT NULL,
  message VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  detected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  INDEX idx_issue_project_status (project_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS test_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  session_id BIGINT NULL,
  reporter_id BIGINT NOT NULL,
  title VARCHAR(150) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_feedback_project FOREIGN KEY (project_id) REFERENCES narrative_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_feedback_session FOREIGN KEY (session_id) REFERENCES playtest_session(id) ON DELETE SET NULL,
  CONSTRAINT fk_feedback_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user(id)
) ENGINE=InnoDB;
