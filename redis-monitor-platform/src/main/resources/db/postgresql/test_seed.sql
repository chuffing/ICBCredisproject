-- Redis监控运维平台测试数据。
-- 仅用于开发、测试环境，不要在生产数据库执行。

SET TIME ZONE 'Asia/Shanghai';

-- 1. 三台已纳管Redis：一主两从。
INSERT INTO redis_instance (
    id, instance_id, instance_name, host_ip, redis_port, role,
    redis_version, run_status, master_host_ip, master_redis_port,
    config_sha256, last_heartbeat_time, management_status
) VALUES
    ('100000000000000001', 'redis-prod-01', 'redis-prod-master',
     '10.10.20.11', '6379', 'MASTER', '7.2.4', '1', '', '',
     repeat('a', 64), LOCALTIMESTAMP, '1'),
    ('100000000000000002', 'redis-prod-02', 'redis-prod-replica-1',
     '10.10.20.12', '6379', 'REPLICA', '7.2.4', '1',
     '10.10.20.11', '6379', repeat('b', 64), LOCALTIMESTAMP, '1'),
    ('100000000000000003', 'redis-prod-03', 'redis-prod-replica-2',
     '10.10.20.13', '6379', 'REPLICA', '7.2.4', '1',
     '10.10.20.11', '6379', repeat('c', 64), LOCALTIMESTAMP, '1');

-- 2. 三台已纳管Sentinel。
INSERT INTO sentinel_node_info (
    id, instance_id, node_name, node_ip, node_port, sentinel_status,
    config_sha256, last_heartbeat_time, management_status
) VALUES
    ('200000000000000001', 'redis-prod-01', 'sentinel-10.10.20.11',
     '10.10.20.11', '26379', '1', repeat('d', 64), LOCALTIMESTAMP, '1'),
    ('200000000000000002', 'redis-prod-02', 'sentinel-10.10.20.12',
     '10.10.20.12', '26379', '1', repeat('e', 64), LOCALTIMESTAMP, '1'),
    ('200000000000000003', 'redis-prod-03', 'sentinel-10.10.20.13',
     '10.10.20.13', '26379', '1', repeat('f', 64), LOCALTIMESTAMP, '1');

-- 3. 三台在线、已纳管执行器。
INSERT INTO executor_info (
    id, instance_id, host_ip, executor_port, executor_status,
    last_heartbeat_time, last_report_time, management_status, executor_token
) VALUES
    ('300000000000000001', 'redis-prod-01', '10.10.20.11', '8081', '1',
     LOCALTIMESTAMP, LOCALTIMESTAMP, '1', 'test-token-node-01'),
    ('300000000000000002', 'redis-prod-02', '10.10.20.12', '8081', '1',
     LOCALTIMESTAMP, LOCALTIMESTAMP, '1', 'test-token-node-02'),
    ('300000000000000003', 'redis-prod-03', '10.10.20.13', '8081', '1',
     LOCALTIMESTAMP, LOCALTIMESTAMP, '1', 'test-token-node-03');

-- 4. 最近一小时的监控指标，每台节点每5分钟一条。
WITH nodes(instance_id, role, run_id, master_host, master_port, node_offset) AS (
    VALUES
        ('redis-prod-01', 'MASTER',  repeat('1', 40), '', '', 0),
        ('redis-prod-02', 'REPLICA', repeat('2', 40), '10.10.20.11', '6379', 3),
        ('redis-prod-03', 'REPLICA', repeat('3', 40), '10.10.20.11', '6379', 6)
), samples(sample_no) AS (
    SELECT generate_series(0, 11)
)
INSERT INTO rms_metric_log (
    id, instance_id, tm, cpu_per, mem_server_per, mem_us, latency,
    io_iik, io_iok, ops, repl_delay, run_id, master_host, master_port,
    role, redis_version, collect_type
)
SELECT
    md5(nodes.instance_id || '-metric-' || samples.sample_no),
    nodes.instance_id,
    LOCALTIMESTAMP - ((11 - samples.sample_no) * INTERVAL '5 minutes'),
    (22 + nodes.node_offset + samples.sample_no % 5)::text,
    (48 + nodes.node_offset + samples.sample_no % 4)::text,
    (1.20 + nodes.node_offset * 0.02 + samples.sample_no * 0.01)::numeric(5,2)::text,
    (0.60 + nodes.node_offset * 0.03 + samples.sample_no * 0.02)::numeric(5,2)::text,
    (320 + nodes.node_offset * 12 + samples.sample_no * 8)::text,
    (290 + nodes.node_offset * 10 + samples.sample_no * 7)::text,
    (7600 + nodes.node_offset * 80 + samples.sample_no * 95)::text,
    CASE WHEN nodes.role = 'MASTER' THEN '0'
         ELSE (1 + nodes.node_offset % 2)::text END,
    nodes.run_id,
    nodes.master_host,
    nodes.master_port,
    nodes.role,
    '7.2.4',
    '0'
FROM nodes CROSS JOIN samples;

-- 5. 三台节点最近一次采集均成功。
INSERT INTO rms_node_status_log (
    id, instance_id, run_id, tm, collect_status,
    error_msg, role, ping_latency
) VALUES
    ('400000000000000001', 'redis-prod-01', repeat('1', 40),
     LOCALTIMESTAMP, '1', '', 'MASTER', '1'),
    ('400000000000000002', 'redis-prod-02', repeat('2', 40),
     LOCALTIMESTAMP, '1', '', 'REPLICA', '2'),
    ('400000000000000003', 'redis-prod-03', repeat('3', 40),
     LOCALTIMESTAMP, '1', '', 'REPLICA', '2');

-- 6. 两条常用告警规则。
INSERT INTO alarm_rule (
    id, rule_id, rule_name, metric_key, metric_name, compare_type,
    threshold, unit, collect_interval, duration, alarm_level,
    silence_duration, scope_type, scope_instance_id,
    enable_status, del_flag, created_by
) VALUES
    ('500000000000000001', 'RULE-CPU-001', 'CPU使用率过高',
     'cpuUsage', 'CPU使用率', '>', '85', '%', '300', '2', '2',
     '1800', '1', '', '1', '0', 'admin'),
    ('500000000000000002', 'RULE-REPL-001', '主从复制延迟过高',
     'replDelay', '主从复制延迟', '>', '5', 's', '300', '2', '1',
     '1800', '2', 'redis-prod-02,redis-prod-03', '1', '0', 'admin');

-- 7. 一条未恢复告警和一条已恢复告警。
INSERT INTO alarm_record (
    id, alarm_no, rule_id, instance_id, metric_key, metric_name,
    alarm_level, alarm_content, trigger_value, threshold, unit,
    trigger_time, recover_time, recover_status
) VALUES
    ('600000000000000001', 'ALARM202610260001', 'RULE-REPL-001',
     'redis-prod-02', 'replDelay', '主从复制延迟', '1',
     'redis-prod-02主从复制延迟超过阈值', '8', '5', 's',
     LOCALTIMESTAMP - INTERVAL '25 minutes', NULL, '0'),
    ('600000000000000002', 'ALARM202610260002', 'RULE-CPU-001',
     'redis-prod-03', 'cpuUsage', 'CPU使用率', '2',
     'redis-prod-03 CPU使用率曾超过阈值', '89', '85', '%',
     LOCALTIMESTAMP - INTERVAL '2 hours',
     LOCALTIMESTAMP - INTERVAL '90 minutes', '1');

INSERT INTO alarm_notice_log (
    id, alarm_record_id, alarm_no, notice_type, receiver,
    send_time, send_status, fail_reason
) VALUES
    ('610000000000000001', '600000000000000001', 'ALARM202610260001',
     'EMAIL', 'ops-test@example.com', LOCALTIMESTAMP - INTERVAL '24 minutes',
     'SUCCESS', '');

-- 8. 一条历史主从切换记录。
INSERT INTO redis_switch_event (
    id, old_master_id, new_master_id, switch_time,
    switch_type, switch_result
) VALUES
    ('700000000000000001', 'redis-old-master', 'redis-prod-01',
     LOCALTIMESTAMP - INTERVAL '1 day', '2', '1');

-- 9. 审计记录。
INSERT INTO audit_log (
    id, operator_id, operator_name, module, operation_type,
    target_id, operation_content, operation_result, operation_time
) VALUES
    ('800000000000000001', 'admin', '测试管理员', '运维模块',
     '故障恢复', 'TASK202610260001', '执行故障恢复测试任务',
     '成功', LOCALTIMESTAMP - INTERVAL '3 hours'),
    ('800000000000000002', 'admin', '测试管理员', '规则模块',
     '新增', 'RULE-CPU-001', '新增CPU使用率告警规则',
     '成功', LOCALTIMESTAMP - INTERVAL '1 day');

-- 10. 一条已经成功完成的故障恢复任务，不会阻塞新任务创建。
INSERT INTO operation_task (
    task_no, task_type, task_status, operation_reason, created_by,
    request_params, failure_reason, termination_reason, terminated_by,
    start_time, end_time, created_time, updated_time
) VALUES (
    'TASK202610260001', 'FAULT_RECOVERY', 'SUCCESS',
    '测试环境历史故障恢复任务', 'admin', '{}', '', '', '',
    LOCALTIMESTAMP - INTERVAL '3 hours',
    LOCALTIMESTAMP - INTERVAL '2 hours 50 minutes',
    LOCALTIMESTAMP - INTERVAL '3 hours',
    LOCALTIMESTAMP - INTERVAL '2 hours 50 minutes'
);

WITH executors(executor_id) AS (
    VALUES
        ('300000000000000001'),
        ('300000000000000002'),
        ('300000000000000003')
), stages(step_no, operation_type) AS (
    VALUES
        (1, 'STOP_SENTINEL'),
        (2, 'STOP_REDIS'),
        (3, 'RESTORE_CONFIG'),
        (4, 'START_REDIS'),
        (5, 'START_SENTINEL'),
        (6, 'VERIFY_TOPOLOGY')
)
INSERT INTO operation_task_step (
    task_no, step_no, executor_id, operation_type, step_status,
    request_params, result_json, error_code, failure_reason,
    dispatch_time, start_time, end_time, created_time, updated_time
)
SELECT
    'TASK202610260001',
    stages.step_no,
    executors.executor_id,
    stages.operation_type,
    'SUCCESS',
    '{}',
    jsonb_build_object('success', true, 'message', '测试步骤执行成功'),
    NULL,
    '',
    LOCALTIMESTAMP - INTERVAL '3 hours' + (stages.step_no * INTERVAL '1 minute'),
    LOCALTIMESTAMP - INTERVAL '3 hours' + (stages.step_no * INTERVAL '1 minute 10 seconds'),
    LOCALTIMESTAMP - INTERVAL '3 hours' + (stages.step_no * INTERVAL '1 minute 40 seconds'),
    LOCALTIMESTAMP - INTERVAL '3 hours',
    LOCALTIMESTAMP - INTERVAL '2 hours 50 minutes'
FROM executors CROSS JOIN stages;

INSERT INTO task_no_sequence (biz_date, current_value)
VALUES (CURRENT_DATE, 1);

-- 11. 只提供一条禁用的安装包元数据，避免不存在的文件被误用于升级。
INSERT INTO redis_version_package (
    package_name, redis_version, storage_path, sha256,
    package_size, package_status, uploaded_by
) VALUES (
    'redis-7.2.4.tar.gz', '7.2.4',
    './data/versionPackages/redis-7.2.4.tar.gz', repeat('9', 64),
    3500000, 'DISABLED', 'admin'
);
