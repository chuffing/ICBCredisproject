-- Redis监控运维平台本地开发数据库完整结构。
-- 仅在local配置下自动执行，不影响远程数据库。

CREATE TABLE IF NOT EXISTS redis_instance (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    instance_name VARCHAR(128) NOT NULL,
    host_ip VARCHAR(64) NOT NULL,
    redis_port VARCHAR(16) NOT NULL,
    role VARCHAR(16) NOT NULL,
    redis_version VARCHAR(64),
    run_status VARCHAR(16) NOT NULL,
    master_host_ip VARCHAR(64),
    master_redis_port VARCHAR(16),
    config_sha256 VARCHAR(64),
    last_heartbeat_time TIMESTAMP WITHOUT TIME ZONE,
    management_status VARCHAR(16) NOT NULL DEFAULT '0',
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_redis_instance_instance_id UNIQUE (instance_id),
    CONSTRAINT uk_redis_instance_address UNIQUE (host_ip, redis_port)
);

CREATE INDEX IF NOT EXISTS idx_redis_instance_management_status
    ON redis_instance (management_status);

CREATE TABLE IF NOT EXISTS sentinel_node_info (
    id VARCHAR(64) PRIMARY KEY,
    node_name VARCHAR(128) NOT NULL,
    node_ip VARCHAR(64) NOT NULL,
    node_port VARCHAR(16) NOT NULL,
    sentinel_status VARCHAR(16) NOT NULL,
    config_sha256 VARCHAR(64),
    last_heartbeat_time TIMESTAMP WITHOUT TIME ZONE,
    management_status VARCHAR(16) NOT NULL DEFAULT '0',
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sentinel_node_address UNIQUE (node_ip, node_port)
);

CREATE INDEX IF NOT EXISTS idx_sentinel_management_status
    ON sentinel_node_info (management_status);

CREATE TABLE IF NOT EXISTS executor_info (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64),
    host_ip VARCHAR(64) NOT NULL,
    executor_port VARCHAR(16) NOT NULL,
    executor_status VARCHAR(16) NOT NULL DEFAULT '0',
    last_heartbeat_time TIMESTAMP WITHOUT TIME ZONE,
    last_report_time TIMESTAMP WITHOUT TIME ZONE,
    management_status VARCHAR(16) NOT NULL DEFAULT '0',
    executor_token VARCHAR(512) NOT NULL,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_executor_info_address UNIQUE (host_ip, executor_port),
    CONSTRAINT uk_executor_info_token UNIQUE (executor_token)
);

CREATE INDEX IF NOT EXISTS idx_executor_status_heartbeat
    ON executor_info (executor_status, last_heartbeat_time);
CREATE INDEX IF NOT EXISTS idx_executor_management_status
    ON executor_info (management_status);
CREATE INDEX IF NOT EXISTS idx_executor_instance_id
    ON executor_info (instance_id);

CREATE TABLE IF NOT EXISTS rms_metric_log (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    tm TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    cpu_per VARCHAR(64),
    mem_server_per VARCHAR(64),
    mem_us VARCHAR(64),
    latency VARCHAR(64),
    io_iik VARCHAR(64),
    io_iok VARCHAR(64),
    ops VARCHAR(64),
    repl_delay VARCHAR(64),
    run_id VARCHAR(128),
    master_host VARCHAR(64),
    master_port VARCHAR(16),
    role VARCHAR(16),
    redis_version VARCHAR(64),
    collect_type VARCHAR(16) NOT NULL DEFAULT '0',
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metric_instance_time
    ON rms_metric_log (instance_id, tm DESC);
CREATE INDEX IF NOT EXISTS idx_metric_time
    ON rms_metric_log (tm);

CREATE TABLE IF NOT EXISTS rms_node_status_log (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(128),
    tm TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    collect_status VARCHAR(16) NOT NULL,
    error_msg VARCHAR(1000),
    role VARCHAR(16),
    ping_latency VARCHAR(64),
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_node_status_instance_time
    ON rms_node_status_log (instance_id, tm DESC);

CREATE TABLE IF NOT EXISTS alarm_rule (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    compare_type VARCHAR(16) NOT NULL,
    threshold VARCHAR(64) NOT NULL,
    unit VARCHAR(32) NOT NULL DEFAULT '',
    collect_interval VARCHAR(32) NOT NULL DEFAULT '300',
    duration VARCHAR(32) NOT NULL DEFAULT '1',
    alarm_level VARCHAR(16) NOT NULL,
    silence_duration VARCHAR(32) NOT NULL DEFAULT '1800',
    scope_type VARCHAR(16) NOT NULL,
    scope_instance_id VARCHAR(1000) NOT NULL DEFAULT '',
    enable_status VARCHAR(16) NOT NULL DEFAULT '1',
    del_flag VARCHAR(16) NOT NULL DEFAULT '0',
    created_by VARCHAR(64) NOT NULL,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alarm_rule_rule_id UNIQUE (rule_id)
);

CREATE INDEX IF NOT EXISTS idx_alarm_rule_status
    ON alarm_rule (del_flag, enable_status);

CREATE TABLE IF NOT EXISTS alarm_record (
    id VARCHAR(64) PRIMARY KEY,
    alarm_no VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    instance_id VARCHAR(64) NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    alarm_level VARCHAR(16) NOT NULL,
    alarm_content VARCHAR(1000) NOT NULL,
    trigger_value VARCHAR(128) NOT NULL,
    threshold VARCHAR(128) NOT NULL,
    unit VARCHAR(32) NOT NULL DEFAULT '',
    trigger_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    recover_time TIMESTAMP WITHOUT TIME ZONE,
    recover_status VARCHAR(16) NOT NULL DEFAULT '0',
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alarm_record_alarm_no UNIQUE (alarm_no)
);

CREATE INDEX IF NOT EXISTS idx_alarm_record_instance_time
    ON alarm_record (instance_id, trigger_time DESC);
CREATE INDEX IF NOT EXISTS idx_alarm_record_rule_instance_time
    ON alarm_record (rule_id, instance_id, trigger_time DESC);
CREATE INDEX IF NOT EXISTS idx_alarm_record_recover_status
    ON alarm_record (recover_status, trigger_time DESC);

CREATE TABLE IF NOT EXISTS alarm_notice_log (
    id VARCHAR(64) PRIMARY KEY,
    alarm_record_id VARCHAR(64) NOT NULL,
    alarm_no VARCHAR(64) NOT NULL,
    notice_type VARCHAR(16) NOT NULL,
    receiver VARCHAR(1000) NOT NULL,
    send_time TIMESTAMP WITHOUT TIME ZONE,
    send_status VARCHAR(16) NOT NULL,
    fail_reason VARCHAR(1000),
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alarm_notice_record
    ON alarm_notice_log (alarm_record_id);

CREATE TABLE IF NOT EXISTS redis_switch_event (
    id VARCHAR(64) PRIMARY KEY,
    old_master_id VARCHAR(64) NOT NULL,
    new_master_id VARCHAR(64) NOT NULL,
    switch_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    switch_type VARCHAR(16) NOT NULL,
    switch_result VARCHAR(16) NOT NULL,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_redis_switch_event_time
    ON redis_switch_event (switch_time DESC);

CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(64) PRIMARY KEY,
    operator_id VARCHAR(64) NOT NULL,
    operator_name VARCHAR(128) NOT NULL DEFAULT '',
    module VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL DEFAULT '',
    operation_content VARCHAR(2000) NOT NULL,
    operation_result VARCHAR(32) NOT NULL,
    operation_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_operation_time
    ON audit_log (operation_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_target
    ON audit_log (module, target_id);

CREATE TABLE IF NOT EXISTS operation_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(32) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    task_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    operation_reason VARCHAR(500) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    request_params JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reason VARCHAR(1000) NOT NULL DEFAULT '',
    termination_reason VARCHAR(500) NOT NULL DEFAULT '',
    terminated_by VARCHAR(64) NOT NULL DEFAULT '',
    terminated_time TIMESTAMP WITHOUT TIME ZONE,
    start_time TIMESTAMP WITHOUT TIME ZONE,
    end_time TIMESTAMP WITHOUT TIME ZONE,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_operation_task_task_no UNIQUE (task_no)
);

CREATE INDEX IF NOT EXISTS idx_operation_task_type_status
    ON operation_task (task_type, task_status);
CREATE INDEX IF NOT EXISTS idx_operation_task_created_time
    ON operation_task (created_time DESC);

CREATE TABLE IF NOT EXISTS operation_task_step (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(32) NOT NULL,
    step_no INTEGER NOT NULL,
    executor_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    step_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    request_params JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(64) NOT NULL DEFAULT '',
    failure_reason VARCHAR(1000) NOT NULL DEFAULT '',
    dispatch_time TIMESTAMP WITHOUT TIME ZONE,
    start_time TIMESTAMP WITHOUT TIME ZONE,
    end_time TIMESTAMP WITHOUT TIME ZONE,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_operation_step_task FOREIGN KEY (task_no)
        REFERENCES operation_task (task_no) ON DELETE CASCADE,
    CONSTRAINT uk_operation_step_executor
        UNIQUE (task_no, step_no, executor_id),
    CONSTRAINT ck_operation_step_no CHECK (step_no > 0)
);

CREATE INDEX IF NOT EXISTS idx_operation_step_stage
    ON operation_task_step (task_no, step_no, step_status);
CREATE INDEX IF NOT EXISTS idx_operation_step_executor_status
    ON operation_task_step (executor_id, step_status);
CREATE INDEX IF NOT EXISTS idx_operation_step_waiting
    ON operation_task_step (step_status, updated_time);

CREATE TABLE IF NOT EXISTS task_no_sequence (
    biz_date DATE PRIMARY KEY,
    current_value INTEGER NOT NULL DEFAULT 0,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_no_sequence_value CHECK (current_value >= 0)
);

CREATE TABLE IF NOT EXISTS redis_version_package (
    id BIGSERIAL PRIMARY KEY,
    package_name VARCHAR(128) NOT NULL,
    redis_version VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    package_size BIGINT NOT NULL,
    package_status VARCHAR(16) NOT NULL,
    uploaded_by VARCHAR(64) NOT NULL,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_redis_version_package_version UNIQUE (redis_version),
    CONSTRAINT uk_redis_version_package_sha256 UNIQUE (sha256),
    CONSTRAINT ck_redis_version_package_status
        CHECK (package_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_redis_version_package_size CHECK (package_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_redis_version_package_created_time
    ON redis_version_package (created_time DESC);

