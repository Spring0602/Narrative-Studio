-- Read-only MySQL 8 checks. Select the intended database explicitly in Navicat.
-- Run verify-extension.sql first. If tables/columns are missing, stop before data checks.
SELECT DATABASE() AS selected_database;
-- Expected: 21 known tables.
SELECT COUNT(*) AS expected_tables_present FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN (
'sys_user','narrative_project','project_member','world_entry','character_profile',
'character_relation','character_knowledge','node_character','story_node','story_choice',
'state_variable','choice_condition','state_effect','playtest_session','playtest_step',
'detected_issue','test_feedback','account_action_token','story_choice_draft','story_release','player_progress');
-- Expected: 4 rows; compare types/defaults/nullability with progression-extension.sql.
SELECT table_name,column_name,column_type,is_nullable,column_default
FROM information_schema.columns WHERE table_schema=DATABASE() AND (
(table_name='state_variable' AND column_name='persistence_scope') OR
(table_name='story_choice' AND column_name='unlock_rule') OR
(table_name='playtest_step' AND column_name IN ('progress_before','progress_after')))
ORDER BY table_name,column_name;
SELECT column_name,column_type,is_nullable,column_default FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='player_progress' ORDER BY ordinal_position;
-- Unique owner/version index, primary key, and two foreign keys must be present.
SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='player_progress' ORDER BY index_name,seq_in_index;
SELECT constraint_name,column_name,referenced_table_name,referenced_column_name
FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='player_progress';
-- Following counts should all be zero after the structure above is verified.
SELECT COUNT(*) AS invalid_scopes FROM state_variable WHERE persistence_scope NOT IN ('SESSION','PROFILE');
SELECT COUNT(*) AS invalid_progress FROM player_progress
WHERE revision<0 OR version_key<0 OR JSON_TYPE(progress_json)<>'OBJECT';
SELECT COUNT(*) AS orphan_release_versions FROM player_progress p
LEFT JOIN story_release r ON r.id=p.version_key AND r.project_id=p.project_id
WHERE p.version_key>0 AND r.id IS NULL;
SELECT project_id,tester_id,version_key,COUNT(*) AS duplicates FROM player_progress
GROUP BY project_id,tester_id,version_key HAVING COUNT(*)>1;
-- SQL validates storage, not rule semantics. Validate rules via backend and real playthrough tests.
