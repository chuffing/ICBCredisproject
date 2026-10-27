-- 清空、重建并填充Redis监控运维平台测试数据库。
-- 该脚本会删除public下的全部业务表，只允许在开发、测试数据库执行。

\set ON_ERROR_STOP on

BEGIN;

DROP TABLE IF EXISTS
    operation_task_step,
    operation_task,
    task_no_sequence,
    alarm_notice_log,
    alarm_record,
    alarm_rule,
    audit_log,
    redis_switch_event,
    redis_version_package,
    rms_node_status_log,
    rms_metric_log,
    executor_info,
    sentinel_node_info,
    redis_instance
CASCADE;

\ir local_schema.sql
\ir test_seed.sql

COMMIT;
