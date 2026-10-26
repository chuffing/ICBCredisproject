-- opsD 监控与纳管能力所需的存量数据库调整。
-- 所有语句均可重复执行，新建本地库已由 local_schema.sql 覆盖相同结构。

ALTER TABLE executor_info
    ADD COLUMN IF NOT EXISTS last_report_time TIMESTAMP WITHOUT TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_executor_host_port
    ON executor_info (host_ip, executor_port);
CREATE UNIQUE INDEX IF NOT EXISTS uk_executor_instance_bound
    ON executor_info (instance_id)
    WHERE instance_id IS NOT NULL AND instance_id <> '';
CREATE INDEX IF NOT EXISTS idx_executor_status_management
    ON executor_info (executor_status, management_status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_redis_instance_name
    ON redis_instance (instance_name);
CREATE INDEX IF NOT EXISTS idx_redis_instance_status
    ON redis_instance (management_status, run_status, role);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sentinel_ip_port
    ON sentinel_node_info (node_ip, node_port);
CREATE INDEX IF NOT EXISTS idx_sentinel_status
    ON sentinel_node_info (management_status, sentinel_status);

CREATE INDEX IF NOT EXISTS idx_alarm_rule_metric
    ON alarm_rule (metric_key);
CREATE INDEX IF NOT EXISTS idx_alarm_record_instance_time
    ON alarm_record (instance_id, trigger_time DESC);
