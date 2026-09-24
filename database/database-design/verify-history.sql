-- Read-only verification. Select the target database explicitly in Navicat.
SELECT DATABASE() AS selected_database;
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='project_save';
SELECT column_name,column_type,is_nullable,column_default
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='project_save' ORDER BY ordinal_position;
SELECT index_name,non_unique,seq_in_index,column_name
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='project_save' ORDER BY index_name,seq_in_index;
SELECT constraint_name,column_name,referenced_table_name,referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema=DATABASE() AND table_name='project_save' AND referenced_table_name IS NOT NULL;
-- Following queries must return no rows.
SELECT project_id,COUNT(*) AS manual_count FROM project_save
WHERE save_kind='MANUAL' GROUP BY project_id HAVING COUNT(*)>5;
SELECT project_id,auto_slot,COUNT(*) AS auto_count FROM project_save
WHERE save_kind='AUTO' GROUP BY project_id,auto_slot HAVING COUNT(*)>1;
SELECT id FROM project_save WHERE revision<1 OR CHAR_LENGTH(content_hash)<>64
OR NOT JSON_VALID(content_snapshot)
OR save_kind NOT IN ('AUTO','MANUAL')
OR (save_kind='MANUAL' AND auto_slot IS NOT NULL)
OR (save_kind='AUTO' AND (auto_slot IS NULL OR auto_slot<>saved_by));
