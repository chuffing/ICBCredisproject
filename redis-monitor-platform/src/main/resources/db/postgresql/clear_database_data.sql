-- 只清空业务数据，保留所有表、索引、约束和序列结构。
-- 仅允许在开发、测试数据库执行。
\set ON_ERROR_STOP on

BEGIN;

TRUNCATE TABLE
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
RESTART IDENTITY CASCADE;

COMMIT;
