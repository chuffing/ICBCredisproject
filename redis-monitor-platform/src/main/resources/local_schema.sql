-- Redis Monitor Platform 建表语句

-- ============================================================
-- 执行器信息表
-- ============================================================
CREATE TABLE IF NOT EXISTS executor_info (
                                             id                  character varying(32) PRIMARY KEY NOT NULL,
    instance_id         character varying(32) DEFAULT ''::character varying NOT NULL,
    host_ip             character varying(20) NOT NULL,
    executor_port       character varying(10) NOT NULL,
    executor_status     character varying(16) DEFAULT '1'::character varying NOT NULL,
    last_heartbeat_time timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_report_time    timestamp,
    management_status   character varying(16) DEFAULT '0'::character varying NOT NULL,
    executor_token      character varying(512) NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE executor_info IS '执行器信息表:记录执行器相关信息';
COMMENT ON COLUMN executor_info.id IS '表内部主键（雪花id）';
COMMENT ON COLUMN executor_info.instance_id IS '执行器绑定的Redis实例id；空串表示Redis未就绪/尚未绑定';
COMMENT ON COLUMN executor_info.host_ip IS '执行器所在服务器IP';
COMMENT ON COLUMN executor_info.executor_port IS '执行器HTTP服务端口';
COMMENT ON COLUMN executor_info.executor_status IS '执行器状态：0=OFFLINE / 1=ONLINE';
COMMENT ON COLUMN executor_info.last_heartbeat_time IS '最近一次心跳时间';
COMMENT ON COLUMN executor_info.last_report_time IS '最近一次Redis/Sentinel状态上报时间';
COMMENT ON COLUMN executor_info.management_status IS '纳管状态：0=INACTIVE / 1=ACTIVE';
COMMENT ON COLUMN executor_info.executor_token IS '执行器认证token（注册时下发）';
COMMENT ON COLUMN executor_info.created_time IS '创建时间';
COMMENT ON COLUMN executor_info.updated_time IS '最后更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_executor_host_port
    ON executor_info(host_ip, executor_port);
-- 一个 Redis 实例只能绑定一个执行器；未绑定执行器统一为空串，用部分索引排除
CREATE UNIQUE INDEX IF NOT EXISTS uk_executor_instance_bound
    ON executor_info(instance_id)
    WHERE instance_id <> '';
CREATE INDEX IF NOT EXISTS idx_executor_instance
    ON executor_info(instance_id);
CREATE INDEX IF NOT EXISTS idx_executor_token
    ON executor_info(executor_token);
CREATE INDEX IF NOT EXISTS idx_executor_management_status
    ON executor_info (management_status);


-- ============================================================
-- Redis实例基础信息表
-- ============================================================
CREATE TABLE IF NOT EXISTS redis_instance (
                                              id                  character varying(32) PRIMARY KEY NOT NULL,
    instance_id         character varying(32) NOT NULL,
    instance_name       character varying(100) NOT NULL,
    host_ip             character varying(20) NOT NULL,
    redis_port          character varying(10) NOT NULL,
    role                character varying(16) NOT NULL,
    redis_version       character varying(32) NOT NULL,
    run_status          character varying(4) NOT NULL,
    master_host_ip      character varying(20) NOT NULL DEFAULT '',
    master_redis_port   character varying(10) NOT NULL DEFAULT '',
    config_sha256       character varying(64) DEFAULT ''::character varying NOT NULL,
    last_heartbeat_time timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    management_status   character varying(16) DEFAULT '0'::character varying NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE redis_instance IS 'Redis实例表:保存已纳管Redis实例的连接、角色、运行状态、主从关系。';
COMMENT ON COLUMN redis_instance.id IS '主键id';
COMMENT ON COLUMN redis_instance.instance_id IS '实例id（雪花id）';
COMMENT ON COLUMN redis_instance.instance_name IS 'Redis实例名称，格式 host:port';
COMMENT ON COLUMN redis_instance.host_ip IS '所在服务器IP';
COMMENT ON COLUMN redis_instance.redis_port IS 'Redis端口，与host_ip共同确定唯一实例';
COMMENT ON COLUMN redis_instance.role IS '节点角色：MASTER/REPLICA';
COMMENT ON COLUMN redis_instance.redis_version IS 'Redis版本号';
COMMENT ON COLUMN redis_instance.run_status IS '运行状态：0=离线/1=正常/2=警告/3=严重';
COMMENT ON COLUMN redis_instance.master_host_ip IS '主节点IP（从节点有值，主节点为空）';
COMMENT ON COLUMN redis_instance.master_redis_port IS '主节点Redis端口（从节点有值，主节点为空）';
COMMENT ON COLUMN redis_instance.config_sha256 IS 'Redis故障恢复备份配置文件的SHA256摘要，非null，默认''';
COMMENT ON COLUMN redis_instance.last_heartbeat_time IS '最近心跳时间';
COMMENT ON COLUMN redis_instance.management_status IS '纳管状态：0=未纳管/1=已纳管';
COMMENT ON COLUMN redis_instance.created_time IS '创建时间';
COMMENT ON COLUMN redis_instance.updated_time IS '最后更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_redis_instance_instance_id
    ON redis_instance(instance_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_redis_instance_name
    ON redis_instance(instance_name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_redis_instance_addresss
    ON redis_instance(host_ip, redis_port);
CREATE INDEX IF NOT EXISTS idx_redis_instance_status
    ON redis_instance(management_status, run_status, role);

-- ============================================================
-- 哨兵节点配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS sentinel_node_info (
                                                  id                  character varying(32) PRIMARY KEY NOT NULL,
    node_name           character varying(64) NOT NULL,
    node_ip             character varying(20) NOT NULL,
    node_port           character varying(10) NOT NULL,
    sentinel_status     character varying(4) DEFAULT '1'::character varying NOT NULL,
    config_sha256       character varying(64) DEFAULT ''::character varying NOT NULL,
    last_heartbeat_time timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    management_status   character varying(16) DEFAULT '0'::character varying NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE sentinel_node_info IS '哨兵节点配置表:存储哨兵模式下节点的配置与在线状态';
COMMENT ON COLUMN sentinel_node_info.id IS '节点id（主键）';
COMMENT ON COLUMN sentinel_node_info.node_name IS '哨兵节点名';
COMMENT ON COLUMN sentinel_node_info.node_ip IS '哨兵节点IP';
COMMENT ON COLUMN sentinel_node_info.node_port IS '哨兵节点端口';
COMMENT ON COLUMN sentinel_node_info.sentinel_status IS '执行器上报的哨兵状态：0=离线/1=在线';
COMMENT ON COLUMN sentinel_node_info.config_sha256 IS 'Redis故障恢复备份配置文件的SHA256摘要，非null，默认''';
COMMENT ON COLUMN sentinel_node_info.last_heartbeat_time IS '最后心跳时间（执行器上报时间）';
COMMENT ON COLUMN sentinel_node_info.management_status IS '纳管状态：0=未纳管/1=已纳管';
COMMENT ON COLUMN sentinel_node_info.created_time IS '创建时间';
COMMENT ON COLUMN sentinel_node_info.updated_time IS '最后更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sentinel_ip_port
    ON sentinel_node_info(node_ip, node_port);
CREATE INDEX IF NOT EXISTS idx_sentinel_status
    ON sentinel_node_info(management_status, sentinel_status);
CREATE INDEX IF NOT EXISTS idx_sentinel_management_status
    ON sentinel_node_info (management_status);

-- ============================================================
-- 告警规则表
-- ============================================================
CREATE TABLE IF NOT EXISTS alarm_rule (
                                          id                  character varying(32) PRIMARY KEY NOT NULL,
    rule_id             character varying(32) NOT NULL,
    rule_name           character varying(128) NOT NULL,
    metric_key          character varying(64) NOT NULL,
    metric_name         character varying(64) NOT NULL,
    compare_type        character varying(8) DEFAULT '>'::character varying NOT NULL,
    threshold           character varying(32) NOT NULL,
    unit                character varying(32),
    collect_interval    character varying(16) DEFAULT '300'::character varying NOT NULL,
    duration            character varying(16) DEFAULT '1'::character varying NOT NULL,
    alarm_level         character varying(4) DEFAULT '1'::character varying NOT NULL,
    silence_duration    character varying(16) DEFAULT '1800'::character varying NOT NULL,
    scope_type          character varying(4) DEFAULT '1'::character varying NOT NULL,
    scope_instance_id   character varying(1024) DEFAULT ''::character varying NOT NULL,
    enable_status       character varying(4) DEFAULT '1'::character varying NOT NULL,
    del_flag            character varying(4) DEFAULT '0'::character varying NOT NULL,
    created_by          character varying(32) NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE alarm_rule IS '告警规则配置表:存储所有监控指标的告警触发规则';
COMMENT ON COLUMN alarm_rule.id IS '主键id';
COMMENT ON COLUMN alarm_rule.rule_id IS '告警规则id（RL_+雪花id），业务唯一键';
COMMENT ON COLUMN alarm_rule.rule_name IS '告警规则名称';
COMMENT ON COLUMN alarm_rule.metric_key IS '监控指标key，如cpuUsage、qps等';
COMMENT ON COLUMN alarm_rule.metric_name IS '监控指标中文名称';
COMMENT ON COLUMN alarm_rule.compare_type IS '比较方式：>、<、=、>=、<=';
COMMENT ON COLUMN alarm_rule.threshold IS '告警阈值';
COMMENT ON COLUMN alarm_rule.unit IS '阈值单位';
COMMENT ON COLUMN alarm_rule.collect_interval IS '采集间隔，单位秒，默认300';
COMMENT ON COLUMN alarm_rule.duration IS '持续触发时长，单位个采集周期，默认1';
COMMENT ON COLUMN alarm_rule.alarm_level IS '告警级别：1=提示/2=警告/3=严重';
COMMENT ON COLUMN alarm_rule.silence_duration IS '告警静默期，单位秒，默认1800';
COMMENT ON COLUMN alarm_rule.scope_type IS '生效范围：1=全部节点/2=指定节点';
COMMENT ON COLUMN alarm_rule.scope_instance_id IS '生效实例id串，逗号分隔，scopeType=2时有效';
COMMENT ON COLUMN alarm_rule.enable_status IS '启用状态：0=禁用/1=启用';
COMMENT ON COLUMN alarm_rule.del_flag IS '逻辑删除标记：0=未删除/1=已删除';
COMMENT ON COLUMN alarm_rule.created_by IS '创建人';
COMMENT ON COLUMN alarm_rule.created_time IS '创建时间';
COMMENT ON COLUMN alarm_rule.updated_time IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_alarm_rule_status
    ON alarm_rule(del_flag, enable_status);
CREATE INDEX IF NOT EXISTS idx_alarm_rule_metric
    ON alarm_rule(metric_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_alarm_rule_rule_id
    ON alarm_rule(rule_id);

-- ============================================================
-- 告警记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS alarm_record (
                                            id                  character varying(32) PRIMARY KEY NOT NULL,
    alarm_no            character varying(64) NOT NULL,
    rule_id             character varying(32),
    instance_id         character varying(32) NOT NULL,
    metric_key          character varying(64) NOT NULL,
    metric_name         character varying(64) NOT NULL,
    alarm_level         character varying(4) NOT NULL,
    alarm_content       character varying(1024) NOT NULL,
    trigger_value       character varying(32) NOT NULL,
    threshold           character varying(32),
    unit                character varying(32),
    trigger_time        timestamp NOT NULL,
    recover_time        timestamp,
    recover_status      character varying(4) DEFAULT '0'::character varying NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE alarm_record IS '告警记录表:存储所有触发的告警事件及恢复状态';
COMMENT ON COLUMN alarm_record.id IS '主键id';
COMMENT ON COLUMN alarm_record.alarm_no IS '告警编号';
COMMENT ON COLUMN alarm_record.rule_id IS '关联告警规则id（主从切换等系统告警为空串）';
COMMENT ON COLUMN alarm_record.instance_id IS '告警触发实例id';
COMMENT ON COLUMN alarm_record.metric_key IS '监控指标key';
COMMENT ON COLUMN alarm_record.metric_name IS '监控指标中文名称';
COMMENT ON COLUMN alarm_record.alarm_level IS '告警级别：1=提示/2=警告/3=严重';
COMMENT ON COLUMN alarm_record.alarm_content IS '告警描述内容';
COMMENT ON COLUMN alarm_record.trigger_value IS '触发时指标值';
COMMENT ON COLUMN alarm_record.threshold IS '告警阈值';
COMMENT ON COLUMN alarm_record.unit IS '阈值单位';
COMMENT ON COLUMN alarm_record.trigger_time IS '告警触发时间';
COMMENT ON COLUMN alarm_record.recover_time IS '告警恢复时间，未恢复为null';
COMMENT ON COLUMN alarm_record.recover_status IS '告警状态：0=未恢复/1=已恢复';
COMMENT ON COLUMN alarm_record.created_time IS '创建时间';
COMMENT ON COLUMN alarm_record.updated_time IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_alarm_record_time
    ON alarm_record(trigger_time DESC);
CREATE INDEX IF NOT EXISTS idx_alarm_record_status
    ON alarm_record(recover_status);
CREATE INDEX IF NOT EXISTS idx_alarm_record_rule_instance
    ON alarm_record(rule_id, instance_id);
CREATE INDEX IF NOT EXISTS idx_alarm_record_instance_time
    ON alarm_record(instance_id, trigger_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_alarm_record_alarm_no
    ON alarm_record(alarm_no);
-- ============================================================
-- 告警通知记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS alarm_notice_log (
                                                id              character varying(32) PRIMARY KEY NOT NULL,
    alarm_record_id character varying(32) NOT NULL,
    alarm_no        character varying(64) NOT NULL,
    notice_type     character varying(16) NOT NULL,
    receiver        character varying(512) NOT NULL,
    send_time       timestamp,
    send_status     character varying(16) DEFAULT '2'::character varying NOT NULL,
    fail_reason     character varying(512),
    created_time    timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE alarm_notice_log IS '告警通知记录表:记录告警通知发送情况';
COMMENT ON COLUMN alarm_notice_log.id IS '主键id';
COMMENT ON COLUMN alarm_notice_log.alarm_record_id IS '告警记录id';
COMMENT ON COLUMN alarm_notice_log.alarm_no IS '告警编号';
COMMENT ON COLUMN alarm_notice_log.notice_type IS '通知类型：1=邮件';
COMMENT ON COLUMN alarm_notice_log.receiver IS '接收人，多个逗号分隔';
COMMENT ON COLUMN alarm_notice_log.send_time IS '发送时间';
COMMENT ON COLUMN alarm_notice_log.send_status IS '发送状态：0=失败/1=成功/2=未发送';
COMMENT ON COLUMN alarm_notice_log.fail_reason IS '失败原因，成功为null';
COMMENT ON COLUMN alarm_notice_log.created_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_notice_alarm
    ON alarm_notice_log(alarm_record_id);
CREATE INDEX IF NOT EXISTS idx_notice_status
    ON alarm_notice_log(send_status);
-- ============================================================
-- 主从切换事件表
-- ============================================================
CREATE TABLE IF NOT EXISTS redis_switch_event (
                                                  id                  character varying(32) PRIMARY KEY NOT NULL,
    old_master_id       character varying(32),
    new_master_id       character varying(32) NOT NULL,
    switch_time         timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    switch_type         character varying(4) NOT NULL,
    switch_result       character varying(4) DEFAULT '1'::character varying NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE redis_switch_event IS '主从切换事件表:存储每次主从角色切换的完整时间记录，用于切换追溯与统计';
COMMENT ON COLUMN redis_switch_event.id IS '主键id';
COMMENT ON COLUMN redis_switch_event.old_master_id IS '原主节点实例id';
COMMENT ON COLUMN redis_switch_event.new_master_id IS '新主节点实例id';
COMMENT ON COLUMN redis_switch_event.switch_time IS '切换发生时间';
COMMENT ON COLUMN redis_switch_event.switch_type IS '切换类型：0=基线/1=自动切换/2=手动切换/3=其他';
COMMENT ON COLUMN redis_switch_event.switch_result IS '切换结果：0=失败/1=成功';
COMMENT ON COLUMN redis_switch_event.created_time IS '创建时间';
COMMENT ON COLUMN redis_switch_event.updated_time IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_switch_event_time
    ON redis_switch_event(switch_time DESC);

-- ============================================================
-- 监控指标数据采集表
-- ============================================================
CREATE TABLE IF NOT EXISTS rms_metric_log (
                                              id                  character varying(32) PRIMARY KEY NOT NULL,
    instance_id         character varying(32) NOT NULL,
    tm                  timestamp NOT NULL,
    cpu_per             character varying(20) NOT NULL,
    mem_server_per      character varying(20) NOT NULL,
    mem_us              character varying(30) NOT NULL,
    latency             character varying(20) NOT NULL,
    ping_latency        character varying(20),
    io_iik              character varying(20) NOT NULL,
    io_iok              character varying(20) NOT NULL,
    ops                 character varying(30) NOT NULL,
    repl_delay          character varying(20),
    run_id              character varying(100) NOT NULL,
    master_host         character varying(20),
    master_port         character varying(10),
    role                character varying(16),
    redis_version       character varying(32) NOT NULL,
    collect_type        character varying(4) DEFAULT '0'::character varying NOT NULL,
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE rms_metric_log IS '监控指标数据采集表:定时采集任务数据记录表';
COMMENT ON COLUMN rms_metric_log.id IS '主键id';
COMMENT ON COLUMN rms_metric_log.instance_id IS '实例id';
COMMENT ON COLUMN rms_metric_log.tm IS '采集时间';
COMMENT ON COLUMN rms_metric_log.cpu_per IS 'CPU占用率（%）';
COMMENT ON COLUMN rms_metric_log.mem_server_per IS '服务器内存占用率（%）';
COMMENT ON COLUMN rms_metric_log.mem_us IS 'Redis内存使用量（GB，执行器按字节/1024^3换算后上报）';
COMMENT ON COLUMN rms_metric_log.latency IS '命令加权平均延迟（毫秒）';
COMMENT ON COLUMN rms_node_status_log.ping_latency IS '执行器ping平台的HTTP往返延迟（毫秒）';
COMMENT ON COLUMN rms_metric_log.io_iik IS '网络输入带宽（KB/s）';
COMMENT ON COLUMN rms_metric_log.io_iok IS '网络输出带宽（KB/s）';
COMMENT ON COLUMN rms_metric_log.ops IS '瞬时QPS（次/秒）';
COMMENT ON COLUMN rms_metric_log.repl_delay IS '主从复制延迟（秒）';
COMMENT ON COLUMN rms_metric_log.run_id IS 'Redis运行id（40位小写十六进制）';
COMMENT ON COLUMN rms_metric_log.master_host IS '主节点host（从节点上报，主节点上报自身地址）';
COMMENT ON COLUMN rms_metric_log.master_port IS '主节点port（从节点上报，主节点上报自身端口）';
COMMENT ON COLUMN rms_metric_log.role IS '节点角色：MASTER/REPLICA';
COMMENT ON COLUMN rms_metric_log.redis_version IS 'Redis版本号';
COMMENT ON COLUMN rms_metric_log.collect_type IS '采集类型：0=自动/1=手动';
COMMENT ON COLUMN rms_metric_log.created_time IS '创建时间';
COMMENT ON COLUMN rms_metric_log.updated_time IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_metric_instance_tm
    ON rms_metric_log(instance_id, tm DESC);
CREATE INDEX IF NOT EXISTS idx_metric_time
    ON rms_metric_log (tm);

-- ============================================================
-- 节点状态采集表
-- ============================================================
/*CREATE TABLE IF NOT EXISTS rms_node_status_log (
                                                   id                  character varying(32) PRIMARY KEY NOT NULL,
    instance_id         character varying(32) NOT NULL,
    run_id              character varying(100) NOT NULL,
    tm                  timestamp NOT NULL,
    collect_status      character varying(4) NOT NULL,
    error_msg           character varying(500),
    role                character varying(16),
    ping_latency        character varying(20),
    created_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time        timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE rms_node_status_log IS '节点状态采集表:定时采集节点状态记录表';
COMMENT ON COLUMN rms_node_status_log.id IS '主键id';
COMMENT ON COLUMN rms_node_status_log.instance_id IS '实例id';
COMMENT ON COLUMN rms_node_status_log.run_id IS 'Redis运行id';
COMMENT ON COLUMN rms_node_status_log.tm IS '采集时间';
COMMENT ON COLUMN rms_node_status_log.collect_status IS '采集时节点状态：0=离线/1=在线';
COMMENT ON COLUMN rms_node_status_log.error_msg IS '失败原因';
COMMENT ON COLUMN rms_node_status_log.role IS '节点角色';
COMMENT ON COLUMN rms_node_status_log.ping_latency IS '执行器ping平台的HTTP往返延迟（毫秒）';
COMMENT ON COLUMN rms_node_status_log.created_time IS '创建时间';
COMMENT ON COLUMN rms_node_status_log.updated_time IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_node_status_instance_time
    ON rms_node_status_log (instance_id, tm DESC);*/

-- ============================================================
-- 操作审计日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
                                         id                character varying(32) PRIMARY KEY NOT NULL,
    operator_id       character varying(64),
    operator_name     character varying(128),
    module            character varying(64),
    operation_type    character varying(64),
    target_id         character varying(64),
    operation_content character varying(1024),
    operation_result  character varying(16),
    operation_time    timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_time      timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time      timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

COMMENT ON TABLE audit_log IS '操作审计日志表:记录平台操作审计信息';
COMMENT ON COLUMN audit_log.id IS '主键id';
COMMENT ON COLUMN audit_log.operator_id IS '操作人统一认证号，无认证体系时默认system';
COMMENT ON COLUMN audit_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN audit_log.module IS '功能模块，如：规则模块、告警记录、报告模块';
COMMENT ON COLUMN audit_log.operation_type IS '操作类型，如：新增、修改、删除、导出、告警恢复';
COMMENT ON COLUMN audit_log.target_id IS '操作对象id，如规则id、告警记录id、导出任务id';
COMMENT ON COLUMN audit_log.operation_content IS '操作内容描述';
COMMENT ON COLUMN audit_log.operation_result IS '操作结果：成功/失败';
COMMENT ON COLUMN audit_log.operation_time IS '操作时间';
COMMENT ON COLUMN audit_log.created_time IS '创建时间';
COMMENT ON COLUMN audit_log.updated_time IS '最后更新时间';
CREATE INDEX IF NOT EXISTS idx_audit_log_operation_time
    ON audit_log (operation_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_module
    ON audit_log(module, operation_type);




=========================================================================================


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
    error_code INTEGER,
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

