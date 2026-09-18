-- Read-only. Select the intended database in Navicat first. Does not create/change any data.
SELECT DATABASE() AS selected_database;
-- Expected: 20 matching tables, not merely 20 arbitrary tables.
SELECT COUNT(*) AS expected_tables_present
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN (
  'sys_user','narrative_project','project_member','world_entry','character_profile',
  'character_relation','character_knowledge','node_character','story_node','story_choice',
  'state_variable','choice_condition','state_effect','playtest_session','playtest_step',
  'detected_issue','test_feedback','account_action_token','story_choice_draft','story_release'
);
-- Expected: 9 rows. Also compare column types/nullability/defaults with schema-extension.sql.
SELECT table_name,column_name,column_type,is_nullable,column_default
FROM information_schema.columns
WHERE table_schema=DATABASE() AND (
  (table_name='sys_user' AND column_name IN ('email','email_verified_at','token_version'))
  OR (table_name='test_feedback' AND column_name='step_id')
  OR (table_name='playtest_step' AND column_name IN ('knowledge_before','knowledge_after'))
  OR (table_name='playtest_session' AND column_name='release_id')
  OR (table_name='story_release' AND column_name IN ('schema_version','content_snapshot'))
)
ORDER BY table_name,ordinal_position;
-- These two story_release columns belong to the new table; 7 columns extend pre-existing tables.
SELECT table_name,constraint_name,constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema=DATABASE()
ORDER BY table_name,constraint_type,constraint_name;
