-- ============================================================
-- Redis Monitor Platform 测试模拟数据（PostgreSQL / Supabase）
--
-- 用途：本地/联调环境一次性灌入可覆盖全部页面与接口的测试数据。
-- 前置：已执行 schema.sql（或对旧库执行 db/schema-upgrade.sql）。
--
-- 数据版本：v1（2026-10）
-- 覆盖场景：
--   - 3 个已纳管 Redis 实例（1 主 2 从）+ 1 个未纳管实例
--   - 4 个执行器：2 个已绑定、1 个 Redis 未就绪未绑定（空串）、1 个离线
--   - 3 个在线哨兵 + 1 个未纳管哨兵
--   - 7 个告警规则（启用/禁用、全部节点/指定节点）
--   - 8 条告警记录（未恢复/已恢复、三级告警、主从切换）
--   - 3 次主从切换事件（基线 + 2 次自动切换）
--   - 最近 7 天、5 分钟粒度的指标与节点状态数据（含 1 个离线实例）
--   - 10 条操作审计日志
--
-- 说明：
--   1. 固定 ID 均以 19/32 位数字/十六进制构造，满足平台 IdValidator：
--      实例/告警记录主键为纯数字（IdValidator.snowflake），
--      规则 ID 为 "RL_" + 19 位数字。
--   2. 指标单位与平台一致：mem_us=GB、latency/ping_latency=ms、repl_delay=s、
--      io_iik/io_iok=KB/s、ops=次/秒。
--   3. 可重复执行：固定行使用 ON CONFLICT DO NOTHING；
--      指标/节点状态先按测试实例删除再重建，避免重复累积。
--   4. 仅用于测试环境，请勿在生产库执行。
-- ============================================================

BEGIN;

-- 测试实例 ID（后续脚本与测试用例统一引用）
--   1900000000000000001  10.10.10.21:6379  MASTER  正常
--   1900000000000000002  10.10.10.22:6379  REPLICA 警告
--   1900000000000000003  10.10.10.23:6379  REPLICA 正常
--   1900000000000000004  10.10.10.24:6379  MASTER  离线（未纳管）

-- ------------------------------------------------------------
-- 0. 清理旧测试时序数据（保留基础数据，便于重复执行）
-- ------------------------------------------------------------
DELETE FROM rms_metric_log
WHERE instance_id IN ('1900000000000000001', '1900000000000000002',
                      '1900000000000000003', '1900000000000000004');

DELETE FROM rms_node_status_log
WHERE instance_id IN ('1900000000000000001', '1900000000000000002',
                      '1900000000000000003', '1900000000000000004');

-- ------------------------------------------------------------
-- 1. Redis 实例
-- ------------------------------------------------------------
INSERT INTO redis_instance
(id, instance_id, instance_name, host_ip, redis_port, role, redis_version, run_status,
 master_host_ip, master_redis_port, last_heartbeat_time, management_status, created_time, updated_time)
VALUES
('1800000000000000001', '1900000000000000001', '10.10.10.21:6379', '10.10.10.21', '6379',
 'MASTER', '7.2.4', '1', NULL, NULL, NOW() - INTERVAL '10 seconds', '1',
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '10 seconds'),
('1800000000000000002', '1900000000000000002', '10.10.10.22:6379', '10.10.10.22', '6379',
 'REPLICA', '7.2.4', '2', '10.10.10.21', '6379', NOW() - INTERVAL '15 seconds', '1',
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '15 seconds'),
('1800000000000000003', '1900000000000000003', '10.10.10.23:6379', '10.10.10.23', '6379',
 'REPLICA', '7.0.15', '1', '10.10.10.21', '6379', NOW() - INTERVAL '20 seconds', '1',
 NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 seconds'),
('1800000000000000004', '1900000000000000004', '10.10.10.24:6379', '10.10.10.24', '6379',
 'MASTER', '6.2.14', '0', NULL, NULL, NOW() - INTERVAL '2 days', '0',
 NOW() - INTERVAL '60 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 2. 执行器
--    执行器1/2 已绑定实例；执行器3 Redis 未就绪（instance_id=''），用于验证二次注册；
--    执行器4 已离线。
-- ------------------------------------------------------------
INSERT INTO executor_info
(id, instance_id, host_ip, executor_port, executor_status, last_heartbeat_time, last_report_time,
 management_status, executor_token, created_time, updated_time)
VALUES
('1810000000000000001', '1900000000000000001', '10.10.10.21', '9101', '1',
 NOW() - INTERVAL '12 seconds', NOW() - INTERVAL '2 minutes', '1',
 '0123456789abcdef0123456789abcdef', NOW() - INTERVAL '30 days', NOW() - INTERVAL '12 seconds'),
('1810000000000000002', '1900000000000000002', '10.10.10.22', '9101', '1',
 NOW() - INTERVAL '18 seconds', NOW() - INTERVAL '2 minutes', '1',
 'fedcba9876543210fedcba9876543210', NOW() - INTERVAL '30 days', NOW() - INTERVAL '18 seconds'),
('1810000000000000003', '', '10.10.10.23', '9101', '1',
 NOW() - INTERVAL '25 seconds', NULL, '0',
 '11112222333344445555666677778888', NOW() - INTERVAL '1 day', NOW() - INTERVAL '25 seconds'),
('1810000000000000004', '1900000000000000004', '10.10.10.24', '9101', '0',
 NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', '0',
 '99998888777766665555444433332222', NOW() - INTERVAL '60 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 3. 哨兵节点（3 在线 + 1 未纳管离线）
-- ------------------------------------------------------------
INSERT INTO sentinel_node_info
(id, node_name, node_ip, node_port, sentinel_status, last_heartbeat_time,
 management_status, created_time, updated_time)
VALUES
('1820000000000000001', 'sentinel-1', '10.10.10.31', '26379', '1', NOW() - INTERVAL '12 seconds', '1',
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '12 seconds'),
('1820000000000000002', 'sentinel-2', '10.10.10.32', '26379', '1', NOW() - INTERVAL '15 seconds', '1',
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '15 seconds'),
('1820000000000000003', 'sentinel-3', '10.10.10.33', '26379', '1', NOW() - INTERVAL '18 seconds', '1',
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '18 seconds'),
('1820000000000000004', 'sentinel-old', '10.10.10.34', '26379', '0', NOW() - INTERVAL '3 days', '0',
 NOW() - INTERVAL '90 days', NOW() - INTERVAL '3 days')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 4. 告警规则
--    注意：同一 metric_key 的“启用”规则不能与已生效节点重叠，
--    否则通过 /alarm/rule/add 新增时会被平台判重。
-- ------------------------------------------------------------
INSERT INTO alarm_rule
(id, rule_id, rule_name, metric_key, metric_name, compare_type, threshold, unit,
 collect_interval, duration, alarm_level, silence_duration, scope_type, scope_instance_id,
 enable_status, del_flag, created_by, created_time, updated_time)
VALUES
('1830000000000000001', 'RL_1900000000000000001', 'CPU使用率过高', 'cpuUsage', 'CPU使用率', '>', '80', '%',
 '300', '1', '2', '1800', '1', '', '1', '0', 'system',
 NOW() - INTERVAL '25 days', NOW() - INTERVAL '2 days'),
('1830000000000000002', 'RL_1900000000000000002', '服务器内存使用率严重', 'serverMemUsage', '服务器内存使用率', '>=', '90', '%',
 '300', '2', '3', '1800', '1', '', '1', '0', 'system',
 NOW() - INTERVAL '25 days', NOW() - INTERVAL '2 days'),
('1830000000000000003', 'RL_1900000000000000003', 'Redis内存使用量超限', 'redisMemUsed', 'Redis内存使用量', '>', '16', 'GB',
 '300', '1', '2', '1800', '1', '', '1', '0', 'system',
 NOW() - INTERVAL '20 days', NOW() - INTERVAL '1 day'),
('1830000000000000004', 'RL_1900000000000000004', 'QPS突增提示', 'qps', 'QPS', '>', '6000', '次/秒',
 '300', '1', '1', '900', '1', '', '1', '0', 'admin',
 NOW() - INTERVAL '18 days', NOW() - INTERVAL '1 day'),
('1830000000000000005', 'RL_1900000000000000005', '平均延迟严重', 'avgLatency', '平均延迟', '>', '5', 'ms',
 '300', '1', '3', '1800', '1', '', '1', '0', 'system',
 NOW() - INTERVAL '15 days', NOW() - INTERVAL '12 hours'),
('1830000000000000006', 'RL_1900000000000000006', '从节点复制延迟', 'replDelay', '主从复制延迟', '>', '1', 's',
 '300', '2', '2', '1800', '2', '1900000000000000002,1900000000000000003', '1', '0', 'admin',
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '6 hours'),
('1830000000000000007', 'RL_1900000000000000007', '网络入带宽过高（已禁用）', 'netInputBandwidth', '网络输入带宽', '>', '800', 'KB/s',
 '300', '1', '1', '1800', '1', '', '0', '0', 'admin',
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '1 day'),
('1830000000000000008', 'RL_1900000000000000008', '已删除的CPU规则', 'cpuUsage', 'CPU使用率', '>', '95', '%',
 '300', '1', '3', '1800', '2', '1900000000000000003', '0', '1', 'system',
 NOW() - INTERVAL '40 days', NOW() - INTERVAL '20 days')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 5. 告警记录（3 未恢复 + 4 已恢复 + 1 主从切换）
-- ------------------------------------------------------------
INSERT INTO alarm_record
(id, alarm_no, rule_id, instance_id, metric_key, metric_name, alarm_level, alarm_content,
 trigger_value, threshold, unit, trigger_time, recover_time, recover_status, created_time, updated_time)
VALUES
-- 未恢复：CPU 过高
('1840000000000000001', 'AL_1900000000000000001', 'RL_1900000000000000001',
 '1900000000000000001', 'cpuUsage', 'CPU使用率', '2',
 '指标【CPU使用率】当前值 92.5% 超过阈值 80%', '92.5', '80', '%',
 NOW() - INTERVAL '35 minutes', NULL, '0',
 NOW() - INTERVAL '35 minutes', NOW() - INTERVAL '35 minutes'),
-- 未恢复：从节点复制延迟
('1840000000000000002', 'AL_1900000000000000002', 'RL_1900000000000000006',
 '1900000000000000002', 'replDelay', '主从复制延迟', '2',
 '指标【主从复制延迟】当前值 3.2s 超过阈值 1s', '3.2', '1', 's',
 NOW() - INTERVAL '20 minutes', NULL, '0',
 NOW() - INTERVAL '20 minutes', NOW() - INTERVAL '20 minutes'),
-- 未恢复：平均延迟严重
('1840000000000000003', 'AL_1900000000000000003', 'RL_1900000000000000005',
 '1900000000000000001', 'avgLatency', '平均延迟', '3',
 '指标【平均延迟】当前值 8.4ms 超过阈值 5ms', '8.4', '5', 'ms',
 NOW() - INTERVAL '8 minutes', NULL, '0',
 NOW() - INTERVAL '8 minutes', NOW() - INTERVAL '8 minutes'),
-- 已恢复：服务器内存
('1840000000000000004', 'AL_1900000000000000004', 'RL_1900000000000000002',
 '1900000000000000003', 'serverMemUsage', '服务器内存使用率', '3',
 '指标【服务器内存使用率】当前值 95.1% 超过阈值 90%', '95.1', '90', '%',
 NOW() - INTERVAL '1 day 4 hours', NOW() - INTERVAL '1 day 3 hours', '1',
 NOW() - INTERVAL '1 day 4 hours', NOW() - INTERVAL '1 day 3 hours'),
-- 已恢复：Redis 内存
('1840000000000000005', 'AL_1900000000000000005', 'RL_1900000000000000003',
 '1900000000000000001', 'redisMemUsed', 'Redis内存使用量', '2',
 '指标【Redis内存使用量】当前值 18.6GB 超过阈值 16GB', '18.6', '16', 'GB',
 NOW() - INTERVAL '2 days 6 hours', NOW() - INTERVAL '2 days 5 hours', '1',
 NOW() - INTERVAL '2 days 6 hours', NOW() - INTERVAL '2 days 5 hours'),
-- 已恢复：从节点 CPU
('1840000000000000006', 'AL_1900000000000000006', 'RL_1900000000000000001',
 '1900000000000000002', 'cpuUsage', 'CPU使用率', '2',
 '指标【CPU使用率】当前值 85.0% 超过阈值 80%', '85.0', '80', '%',
 NOW() - INTERVAL '2 days 10 hours', NOW() - INTERVAL '2 days 9 hours', '1',
 NOW() - INTERVAL '2 days 10 hours', NOW() - INTERVAL '2 days 9 hours'),
-- 已恢复：QPS 突增
('1840000000000000007', 'AL_1900000000000000007', 'RL_1900000000000000004',
 '1900000000000000001', 'qps', 'QPS', '1',
 '指标【QPS】当前值 8120次/秒 超过阈值 6000次/秒', '8120', '6000', '次/秒',
 NOW() - INTERVAL '3 days 2 hours', NOW() - INTERVAL '3 days 1 hour', '1',
 NOW() - INTERVAL '3 days 2 hours', NOW() - INTERVAL '3 days 1 hour'),
-- 主从切换系统告警（rule_id 为空串）
('1840000000000000008', 'AL_1900000000000000008', '',
 '1900000000000000001', 'masterSwitch', '主从切换', '2',
 '检测到主从切换：原主节点 1900000000000000002 → 新主节点 1900000000000000001', '', NULL, '',
 NOW() - INTERVAL '1 day 2 hours', NULL, '0',
 NOW() - INTERVAL '1 day 2 hours', NOW() - INTERVAL '1 day 2 hours')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 6. 告警通知记录
-- ------------------------------------------------------------
INSERT INTO alarm_notice_log
(id, alarm_record_id, alarm_no, notice_type, receiver, send_time, send_status, fail_reason, created_time)
VALUES
('1850000000000000001', '1840000000000000001', 'AL_1900000000000000001', '1',
 'ops@example.com,admin@example.com', NULL, '2', NULL, NOW() - INTERVAL '35 minutes'),
('1850000000000000002', '1840000000000000002', 'AL_1900000000000000002', '1',
 'ops@example.com', NULL, '2', NULL, NOW() - INTERVAL '20 minutes'),
('1850000000000000003', '1840000000000000004', 'AL_1900000000000000004', '1',
 'ops@example.com', NOW() - INTERVAL '1 day 4 hours', '1', NULL, NOW() - INTERVAL '1 day 4 hours'),
('1850000000000000004', '1840000000000000007', 'AL_1900000000000000007', '1',
 'admin@example.com', NOW() - INTERVAL '3 days 2 hours', '0', 'SMTP connect timeout',
 NOW() - INTERVAL '3 days 2 hours')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 7. 主从切换事件（基线 + 2 次自动切换，最近一次回到实例1）
-- ------------------------------------------------------------
INSERT INTO redis_switch_event
(id, old_master_id, new_master_id, switch_time, switch_type, switch_result, created_time, updated_time)
VALUES
('1860000000000000001', NULL, '1900000000000000001', NOW() - INTERVAL '7 days', '0', '1',
 NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
('1860000000000000002', '1900000000000000001', '1900000000000000002', NOW() - INTERVAL '3 days 6 hours', '1', '1',
 NOW() - INTERVAL '3 days 6 hours', NOW() - INTERVAL '3 days 6 hours'),
('1860000000000000003', '1900000000000000002', '1900000000000000001', NOW() - INTERVAL '1 day 2 hours', '1', '1',
 NOW() - INTERVAL '1 day 2 hours', NOW() - INTERVAL '1 day 2 hours')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 8. 最近 7 天采集指标（5 分钟粒度，4 个实例）
--    mem_us 为 GB；latency 为 ms；repl_delay 为 s；io 为 KB/s。
-- ------------------------------------------------------------
INSERT INTO rms_metric_log
(id, instance_id, tm, cpu_per, mem_server_per, mem_us, latency, io_iik, io_iok, ops,
 repl_delay, run_id, master_host, master_port, role, redis_version, collect_type,
 created_time, updated_time)
SELECT
    md5(i.instance_id || '|' || gs::text)                                        AS id,
    i.instance_id,
    gs                                                                           AS tm,
    to_char(35 + 30 * sin(extract(epoch FROM gs) / 3600.0) + random() * 8, 'FM990.00')  AS cpu_per,
    to_char(45 + 15 * sin(extract(epoch FROM gs) / 5400.0) + random() * 5, 'FM990.00')  AS mem_server_per,
    to_char(6 + 3 * sin(extract(epoch FROM gs) / 7200.0) + random() * 0.8, 'FM990.000') AS mem_us,
    to_char(0.6 + 0.4 * sin(extract(epoch FROM gs) / 1800.0) + random() * 0.3, 'FM990.000') AS latency,
    to_char(120 + 80 * sin(extract(epoch FROM gs) / 3600.0) + random() * 40, 'FM990.00') AS io_iik,
    to_char(90 + 60 * sin(extract(epoch FROM gs) / 3600.0) + random() * 30, 'FM990.00')  AS io_iok,
    to_char(3000 + 1500 * sin(extract(epoch FROM gs) / 2400.0) + random() * 500, 'FM9999990.00') AS ops,
    CASE WHEN i.role = 'REPLICA'
         THEN to_char(0.2 + random() * 0.9, 'FM990.00')
         ELSE '0' END                                                             AS repl_delay,
    substr(md5('run-' || i.instance_id) || md5(i.instance_id), 1, 40)             AS run_id,
    CASE WHEN i.role = 'MASTER' THEN i.host_ip ELSE '10.10.10.21' END             AS master_host,
    CASE WHEN i.role = 'MASTER' THEN i.redis_port ELSE '6379' END                 AS master_port,
    i.role,
    i.redis_version,
    '0'                                                                           AS collect_type,
    gs                                                                            AS created_time,
    gs                                                                            AS updated_time
FROM redis_instance i
CROSS JOIN generate_series(
        date_trunc('hour', NOW()) - INTERVAL '7 days',
        date_trunc('minute', NOW()),
        INTERVAL '5 minutes') AS gs
WHERE i.instance_id IN ('1900000000000000001', '1900000000000000002',
                        '1900000000000000003', '1900000000000000004')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 9. 最近 7 天节点状态（5 分钟粒度，ping_latency 为执行器 ping 平台延迟 ms）
-- ------------------------------------------------------------
INSERT INTO rms_node_status_log
(id, instance_id, run_id, tm, collect_status, error_msg, role, ping_latency, created_time, updated_time)
SELECT
    md5('node|' || s.instance_id || '|' || s.gs::text)                            AS id,
    s.instance_id,
    substr(md5('run-' || s.instance_id) || md5(s.instance_id), 1, 40)              AS run_id,
    s.gs                                                                          AS tm,
    CASE
        WHEN s.instance_id = '1900000000000000004' AND s.gs > NOW() - INTERVAL '1 day' THEN '0'
        WHEN s.roll < 0.02 THEN '0'
        ELSE '1'
    END                                                                           AS collect_status,
    CASE
        WHEN s.instance_id = '1900000000000000004' AND s.gs > NOW() - INTERVAL '1 day'
            THEN 'connect timeout: 10.10.10.24:6379'
        WHEN s.roll < 0.02 THEN 'redis ping timeout'
        ELSE NULL
    END                                                                           AS error_msg,
    s.role,
    to_char(2 + s.roll * 12, 'FM990.00')                                          AS ping_latency,
    s.gs                                                                          AS created_time,
    s.gs                                                                          AS updated_time
FROM (
    SELECT i.instance_id, i.role, i.redis_version, gs, random() AS roll
    FROM redis_instance i
    CROSS JOIN generate_series(
            date_trunc('hour', NOW()) - INTERVAL '7 days',
            date_trunc('minute', NOW()),
            INTERVAL '5 minutes') AS gs
    WHERE i.instance_id IN ('1900000000000000001', '1900000000000000002',
                            '1900000000000000003', '1900000000000000004')
) s
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 10. 操作审计日志
-- ------------------------------------------------------------
INSERT INTO audit_log
(id, operator_id, operator_name, module, operation_type, target_id, operation_content,
 operation_result, operation_time, created_time, updated_time)
VALUES
('1870000000000000001', 'system', '系统', '规则模块', '新增', 'RL_1900000000000000001',
 '新增告警规则：CPU使用率过高', '成功', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
('1870000000000000002', 'admin', '管理员', '规则模块', '修改', 'RL_1900000000000000004',
 '修改告警规则：QPS突增提示', '成功', NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days'),
('1870000000000000003', 'system', '系统', '规则模块', '删除', 'RL_1900000000000000008',
 '删除告警规则：已删除的CPU规则', '成功', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
('1870000000000000004', 'system', '系统', '告警记录', '自动恢复', '1840000000000000004',
 '指标连续2周期回落阈值以下，自动恢复告警：AL_1900000000000000004', '成功',
 NOW() - INTERVAL '1 day 3 hours', NOW() - INTERVAL '1 day 3 hours', NOW() - INTERVAL '1 day 3 hours'),
('1870000000000000005', 'system', '系统', '告警记录', '告警恢复', '1840000000000000006',
 '人工确认恢复告警：AL_1900000000000000006', '成功',
 NOW() - INTERVAL '2 days 9 hours', NOW() - INTERVAL '2 days 9 hours', NOW() - INTERVAL '2 days 9 hours'),
('1870000000000000006', 'admin', '管理员', '报告模块', '导出', 'EP_1900000000000000101',
 '导出Redis运行报告，实例：1900000000000000001，报告类型：日报', '成功',
 NOW() - INTERVAL '2 days 2 hours', NOW() - INTERVAL '2 days 2 hours', NOW() - INTERVAL '2 days 2 hours'),
('1870000000000000007', 'admin', '管理员', '报告模块', '导出', 'EP_1900000000000000102',
 '导出Redis运行报告，实例：1900000000000000002，报告类型：周报', '失败',
 NOW() - INTERVAL '1 day 5 hours', NOW() - INTERVAL '1 day 5 hours', NOW() - INTERVAL '1 day 5 hours'),
('1870000000000000008', 'system', '系统', '规则模块', '修改', 'RL_1900000000000000006',
 '切换告警规则启用状态：从节点复制延迟 -> 启用', '成功',
 NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours'),
('1870000000000000009', 'system', '系统', '告警记录', '自动恢复', '1840000000000000007',
 '指标连续2周期回落阈值以下，自动恢复告警：AL_1900000000000000007', '成功',
 NOW() - INTERVAL '3 days 1 hour', NOW() - INTERVAL '3 days 1 hour', NOW() - INTERVAL '3 days 1 hour'),
('1870000000000000010', 'admin', '管理员', '运维模块', '服务器切换', '1860000000000000003',
 '服务器切换：1900000000000000002 -> 1900000000000000001', '成功',
 NOW() - INTERVAL '1 day 2 hours', NOW() - INTERVAL '1 day 2 hours', NOW() - INTERVAL '1 day 2 hours')
ON CONFLICT DO NOTHING;

COMMIT;

-- ============================================================
-- 常用验证 SQL（执行后手工抽查）
-- ============================================================
-- SELECT COUNT(*) FROM redis_instance;                    -- 期望 4
-- SELECT COUNT(*) FROM executor_info;                     -- 期望 4
-- SELECT COUNT(*) FROM sentinel_node_info;                -- 期望 4
-- SELECT COUNT(*) FROM alarm_rule WHERE del_flag = '0';   -- 期望 7
-- SELECT COUNT(*) FROM alarm_record;                      -- 期望 8
-- SELECT COUNT(*) FROM rms_metric_log;                    -- 期望 8064（4*7*288）
-- SELECT COUNT(*) FROM rms_node_status_log;               -- 期望 8064
-- SELECT COUNT(*) FROM audit_log;                         -- 期望 10
