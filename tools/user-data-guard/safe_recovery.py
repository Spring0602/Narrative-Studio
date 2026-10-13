"""Fail-closed, insert-only recovery. No cross-database identity guessing."""
import hashlib
import re
from datetime import datetime


def fingerprint(g, rows):
    return hashlib.sha256(g.canonical_rows(rows).encode('utf-8')).hexdigest()


def content_manifest(g, mysql, db):
    available = set(mysql.tables(db))
    return {table: fingerprint(g, mysql.fetch_rows(db, table)[0])
            for table in sorted(g.CONTENT_TABLES) if table in available}


def restore(g, cfg, mysql, target_db, vault, source='vault', from_db=None,
            do_backup=True, mode='ignore', on_conflict='abort',
            disable_fk_checks=False, allow_missing_tables=False, dry_run=False):
    out = dict(ok=False, error='', backup=None, stats={}, warnings=[], notes=[],
               verified=False, dry_run=dry_run)

    def fail(message):
        out['error'] = message
        return out

    # Keep old CLI switches parseable, but never let them bypass safety checks.
    if (source != 'vault' or from_db or not do_backup or mode != 'ignore'
            or on_conflict != 'abort' or disable_fk_checks or allow_missing_tables):
        return fail('仅支持完整快照、强制备份、保留外键的缺失行恢复；不支持跨库搬运、覆盖或跳过冲突。')
    if target_db not in g.whitelist_dbs(cfg):
        return fail('目标库不在白名单，拒绝恢复。')
    origin = dict(host=str(cfg['host']), port=int(cfg['port']), database=target_db)
    if vault.get('version') != 3 or vault.get('source') != origin:
        return fail('需要同一连接/数据库的 v3 快照。旧快照请保留，不要在数据丢失后重新 init 覆盖它。')
    if not re.fullmatch(r'[A-Za-z0-9_]+', target_db):
        return fail('数据库名称只允许字母、数字和下划线。')
    if target_db not in mysql.schemas():
        return fail('目标库不存在；先核对并恢复完整数据库结构，不自动建库。')
    try:
        rows, problems = g.load_snapshot_rows(vault)
    except (ValueError, TypeError, KeyError, AttributeError) as exc:
        return fail('快照结构损坏：' + str(exc))
    if problems:
        return fail('；'.join(problems))
    meta = vault.get('tables', {})
    if not meta or set(rows) != set(meta) or not any(rows.values()):
        return fail('快照为空或表清单不一致。')
    if set(meta) - set(g.TABLE_ORDER):
        return fail('快照包含不支持的表。')
    if not vault.get('content_manifest') or content_manifest(g, mysql, target_db) != vault['content_manifest']:
        return fail('作品内容或 ID 与快照不一致；拒绝按同名项目或复用 ID 猜测恢复关系。')
    order = [t for t in g.TABLE_ORDER if t in meta]
    if set(order) - set(mysql.tables(target_db)):
        return fail('目标库缺少快照表，拒绝部分恢复。')
    engines = mysql.rows("SELECT table_name, engine FROM information_schema.tables "
                        "WHERE table_schema=DATABASE()", db=target_db)
    if any(dict(engines).get(t, '').upper() != 'INNODB' for t in order):
        return fail('恢复表必须全部为 InnoDB，才能保证事务回滚。')
    current, planned = {}, {}
    for table in order:
        saved = rows[table]
        columns = mysql.columns(target_db, table)
        if (not isinstance(saved, list) or not meta[table].get('columns')
                or set(meta[table]['columns']) != set(columns)
                or any(meta[table]['columns'][c] != columns[c]['type'] for c in columns)):
            return fail(f'{table} 字段不兼容，需先制定明确迁移方案。')
        if any(not isinstance(r, dict) or set(r) != set(columns) or r.get('id') is None for r in saved):
            return fail(f'{table} 快照行结构不完整。')
        if len({str(r['id']) for r in saved}) != len(saved):
            return fail(f'{table} 快照主键重复。')
        target_rows = mysql.fetch_rows(target_db, table)[0]
        current[table] = {str(r['id']): r for r in target_rows}
        planned[table] = []
        for row in saved:
            existing = current[table].get(str(row['id']))
            if existing is not None:
                # Preserve current password/token state, never restore stale credentials.
                same = (row.get('username') == existing.get('username')
                        if table == 'sys_user' else g.canonical_rows([row]) == g.canonical_rows([existing]))
                if not same:
                    return fail(f'{table} id={row["id"]} 内容/身份冲突，未写入。')
                continue
            if table == 'sys_user' and any(r.get('username') == row.get('username') for r in target_rows):
                return fail('用户名对应不同 ID，拒绝自动映射账号及其权限。')
            planned[table].append(row)
        out['stats'][table] = dict(planned=len(planned[table]), before=len(target_rows), after=None)

    parents = {t: {**current[t], **{str(r['id']): r for r in planned[t]}} for t in order}
    for table in g.CONTENT_TABLES & set(g.FK_PARENT.values()):
        if table in mysql.tables(target_db):
            parents[table] = {str(r['id']): r for r in mysql.fetch_rows(target_db, table)[0]}
    for table in order:
        for row in planned[table]:
            for col, parent in g.FK_PARENT.items():
                value = row.get(col)
                if value is None:
                    continue
                related = parents.get(parent, {}).get(str(value))
                if related is None:
                    return fail(f'{table}.{col} 父记录缺失，拒绝恢复。')
                if parent in ('story_node', 'story_choice', 'story_release', 'playtest_session'):
                    project = row.get('project_id')
                    if project is None and row.get('session_id') is not None:
                        session = parents.get('playtest_session', {}).get(str(row['session_id']), {})
                        project = session.get('project_id')
                    if project is not None and str(related.get('project_id')) != str(project):
                        return fail(f'{table}.{col} 跨项目引用，拒绝恢复。')
                if col == 'step_id' and row.get('session_id') is not None:
                    if str(related.get('session_id')) != str(row['session_id']):
                        return fail('反馈步骤不属于指定会话。')
            if table == 'player_progress' and row.get('version_key', 0) != 0:
                release = parents.get('story_release', {}).get(str(row['version_key']))
                if release is None or str(release.get('project_id')) != str(row['project_id']):
                    return fail('player_progress.version_key 发布版本归属不符。')

    # Plain INSERT is intentional: unique-key races/errors must abort, not be ignored.
    sql = ["SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';",
           'SET FOREIGN_KEY_CHECKS=1;', 'SET NAMES utf8mb4;',
           'CREATE TEMPORARY TABLE guard_assert (ok INT NOT NULL) ENGINE=InnoDB;',
           'START TRANSACTION;']
    for table in order:
        columns = list(meta[table]['columns'])
        if any(not re.fullmatch(r'[A-Za-z0-9_]+', c) for c in columns):
            return fail('非法字段名。')
        binary = set(meta[table].get('base64_columns', []))
        for row in planned[table]:
            values = ','.join(g.sql_literal(row[c], c in binary) for c in columns)
            sql.append(f"INSERT INTO `{table}` ({','.join('`'+c+'`' for c in columns)}) VALUES ({values});")
            sql.append('INSERT INTO guard_assert VALUES (IF(ROW_COUNT()=1,1,NULL));')
    sql += ['COMMIT;', 'DROP TEMPORARY TABLE guard_assert;']
    if dry_run:
        out.update(ok=True, notes=['预检通过，未备份、未写库；不代表真实 MySQL 验收通过。'])
        return out
    if not any(planned.values()):
        out.update(ok=True, verified=True, notes=['所有记录已存在且通过核对，无需写入。'])
        return out
    backup = g.BACKUP_DIR / f"{target_db}_before_restore_{datetime.now():%Y%m%d_%H%M%S_%f}.sql"
    try:
        mysql.dump_db(target_db, backup)
        out['backup'] = str(backup)
        mysql.sql('\n'.join(sql), db=target_db)
    except RuntimeError as exc:
        return fail('备份或事务执行失败，请保留日志与备份并核查；未报告恢复成功：' + str(exc))
    for table in order:
        actual = {str(r['id']): r for r in mysql.fetch_rows(target_db, table)[0]}
        out['stats'][table]['after'] = len(actual)
        for row in planned[table]:
            if g.canonical_rows([actual.get(str(row['id']), {})]) != g.canonical_rows([row]):
                return fail(f'{table} 写后逐行核验失败；事务已提交，请停止后续操作并人工核查备份。')
    out.update(ok=True, verified=True)
    return out
