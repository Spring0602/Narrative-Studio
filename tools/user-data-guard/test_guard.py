"""Offline regression tests: no MySQL process and no real database writes."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import guard as g
import safe_recovery as safe


class FakeMySql:
    def __init__(self):
        self.data = {
            'sys_user': [{'id': 1, 'username': 'alice'}],
            'narrative_project': [{'id': 10, 'name': 'same-name', 'owner_id': 1}],
            'story_node': [], 'story_choice': [], 'story_release': [],
            'project_member': [], 'node_character': [{'node_id': 2, 'character_id': 3}],
        }
        self.columns_by_table = {
            'sys_user': {'id': 'bigint', 'username': 'varchar'},
            'project_member': {'id': 'bigint', 'project_id': 'bigint', 'user_id': 'bigint', 'role': 'varchar'},
        }
        self.calls = []
        self.failure = False
        self.apply = True
        self.engine = 'InnoDB'
        self.expected = {}

    def schemas(self): return ['test', 'unrelated']
    def tables(self, db): return list(self.data)
    def fetch_rows(self, db, table): return copy.deepcopy(self.data.get(table, [])), {}
    def count(self, db, table): return len(self.data[table])
    def columns(self, db, table):
        return {c: dict(type=t, nullable=False, default=None, extra='')
                for c, t in self.columns_by_table[table].items()}
    def rows(self, sql, db=None): return [[t, self.engine] for t in self.data]
    def dump_db(self, db, path): self.calls.append(('backup', db))
    def sql(self, sql, db=None):
        self.calls.append(('sql', sql))
        if self.failure: raise RuntimeError('synthetic transaction error')
        if self.apply:
            for table, rows in self.expected.items():
                ids = {r['id'] for r in self.data[table]}
                self.data[table].extend(copy.deepcopy([r for r in rows if r['id'] not in ids]))


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.patch = patch.object(g, 'VAULT_DIR', Path(self.tmp.name))
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.db = FakeMySql()
        self.cfg = dict(g.DEFAULT_CONFIG, target_db='test')
        self.rows = {
            'sys_user': [{'id': 1, 'username': 'alice'}],
            'project_member': [{'id': 2, 'project_id': 10, 'user_id': 1, 'role': 'OWNER'}],
        }
        self.vault = dict(version=3, created_at='offline-test', source=dict(host='127.0.0.1', port=3306, database='test'),
                          content_manifest=safe.content_manifest(g, self.db, 'test'))
        self.snapshot()

    def snapshot(self):
        self.vault['tables'] = {t: dict(count=len(r), hash=g.sha16(g.canonical_rows(r)) if r else '',
                                     columns=self.db.columns_by_table[t], base64_columns=[])
                                for t, r in self.rows.items()}
        (g.VAULT_DIR / 'user_data.json').write_text(json.dumps(self.rows), encoding='utf-8')
        self.db.expected = copy.deepcopy(self.rows)

    def restore(self, **kw): return g.restore_into(self.cfg, self.db, 'test', self.vault, **kw)
    def rejected(self, **kw):
        result = self.restore(**kw)
        self.assertFalse(result['ok'], result)
        self.assertEqual(self.db.calls, [])

    def test_valid_restore_and_idempotent_retry(self):
        result = self.restore()
        self.assertTrue(result['ok'], result)
        self.assertTrue(result['verified'])
        self.assertEqual(self.db.calls[0][0], 'backup')
        sql = self.db.calls[1][1]
        self.assertIn('FOREIGN_KEY_CHECKS=1', sql)
        self.assertNotIn('INSERT IGNORE', sql)
        self.assertNotIn('REPLACE INTO', sql)
        self.assertLess(sql.index('ROW_COUNT()'), sql.index('COMMIT'))
        self.db.calls.clear()
        self.assertTrue(self.restore()['ok'])
        self.assertEqual(self.db.calls, [])

    def test_dry_run_is_not_write_verification(self):
        result = self.restore(dry_run=True)
        self.assertTrue(result['ok'])
        self.assertFalse(result['verified'])
        self.assertEqual(self.db.calls, [])

    def test_project_id_reused_by_other_project(self):
        self.db.data['narrative_project'] = [dict(id=10, name='other', owner_id=1),
                                              dict(id=20, name='same-name', owner_id=1)]
        self.rejected()

    def test_duplicate_project_name_is_not_identity(self):
        self.db.data['narrative_project'].append(dict(id=20, name='same-name', owner_id=1))
        self.rejected()

    def test_node_id_reused(self):
        self.db.data['story_node'] = [dict(id=1, project_id=10)]
        self.rejected()

    def test_variable_content_changed(self):
        self.db.data['state_variable'] = [dict(id=7, project_id=10, name='changed')]
        self.rejected()

    def test_backup_failure_never_executes_insert(self):
        with patch.object(self.db, 'dump_db', side_effect=RuntimeError('backup failed')):
            self.assertFalse(self.restore()['ok'])
        self.assertEqual(self.db.calls, [])

    def test_password_change_is_preserved(self):
        self.db.columns_by_table['sys_user']['password_hash'] = 'varchar'
        self.rows['sys_user'][0]['password_hash'] = 'old-test-hash'
        self.db.data['sys_user'][0]['password_hash'] = 'new-test-hash'
        self.snapshot()
        self.assertTrue(self.restore()['ok'])
        self.assertEqual(self.db.data['sys_user'][0]['password_hash'], 'new-test-hash')

    def test_user_id_occupied(self):
        self.db.data['sys_user'][0]['username'] = 'bob'
        self.rejected()

    def test_username_moved_to_another_id(self):
        self.db.data['sys_user'][0]['id'] = 99
        self.rejected()

    def test_existing_member_changed_role(self):
        self.db.data['project_member'] = [dict(self.rows['project_member'][0], role='TESTER')]
        self.rejected()

    def test_missing_parent(self):
        self.rows['project_member'][0]['user_id'] = 8
        self.snapshot()
        self.rejected()

    def test_missing_snapshot(self):
        (g.VAULT_DIR / 'user_data.json').unlink()
        self.rejected()

    def test_tampered_snapshot(self):
        (g.VAULT_DIR / 'user_data.json').write_text('{}', encoding='utf-8')
        self.rejected()

    def test_missing_hash(self):
        self.vault['tables']['sys_user']['hash'] = ''
        self.rejected()

    def test_missing_table(self):
        del self.db.data['project_member']
        self.rejected()

    def test_changed_schema(self):
        self.db.columns_by_table['project_member'] = dict(self.db.columns_by_table['project_member'], extra='varchar')
        self.rejected()

    def test_nontransactional_engine(self):
        self.db.engine = 'MyISAM'
        self.rejected()

    def test_different_source_and_old_snapshot(self):
        self.vault['source']['database'] = 'other'
        self.rejected()
        self.vault['source']['database'] = 'test'
        self.vault['version'] = 2
        self.rejected()

    def test_nonwhitelisted_database(self):
        self.cfg['target_db'] = 'other'
        self.rejected()

    def test_unsafe_switches_rejected(self):
        for options in (dict(mode='replace'), dict(on_conflict='skip'), dict(do_backup=False),
                        dict(disable_fk_checks=True), dict(allow_missing_tables=True), dict(source='live')):
            with self.subTest(options=options): self.rejected(**options)

    def test_sql_failure_not_success(self):
        self.db.failure = True
        self.assertFalse(self.restore()['ok'])

    def test_partial_write_not_success(self):
        self.db.apply = False
        result = self.restore()
        self.assertFalse(result['ok'])
        self.assertIn('事务已提交', result['error'])

    def test_watch_does_not_restore_even_with_old_auto_setting(self):
        cfg = dict(self.cfg, auto_restore=True)
        args = g.build_parser().parse_args(['watch', '--auto-restore', '--once'])
        with patch.object(g, 'load_vault', return_value=self.vault), patch.object(g, 'log'), \
             patch.object(g, 'check_db', return_value=dict(level=g.LEVEL_WIPED, notes=[])), \
             patch.object(g, 'restore_into') as restore:
            self.assertEqual(g.cmd_watch(cfg, self.db, args), 0)
            restore.assert_not_called()

    def test_normal_deletion_only_alerts(self):
        self.rows['sys_user'].append(dict(id=2, username='bob'))
        self.snapshot()
        self.assertEqual(g.check_db(self.cfg, self.db, 'test', self.vault)['level'], g.LEVEL_ALERT)

    def test_raw_json_output_not_double_escaped(self):
        mysql = object.__new__(g.MySql)
        mysql.cfg = self.cfg; mysql.mysql = 'never-run'
        result = type('Result', (), dict(returncode=0, stdout=b'{"value":"a\\nb"}\n', stderr=b''))()
        with patch.object(g.subprocess, 'run', return_value=result) as run:
            self.assertEqual(json.loads(mysql.one('SELECT JSON_OBJECT()')), {'value': 'a\nb'})
            self.assertIn('--raw', run.call_args.args[0])


if __name__ == '__main__':
    unittest.main()
