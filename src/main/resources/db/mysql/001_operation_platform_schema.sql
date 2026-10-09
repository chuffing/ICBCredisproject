-- Redis 运维平台第一阶段数据库结构。
-- 当前只包含执行器管理、运维任务调度和版本包管理，不包含监控指标与告警表。

CREATE DATABASE IF NOT EXISTS redis_operation_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE redis_operation_platform;

-- 执行器注册与心跳状态。
CREATE TABLE IF NOT EXISTS executor_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    executor_id VARCHAR(64) NOT NULL COMMENT '执行器唯一编号',
    executor_name VARCHAR(128) NOT NULL COMMENT '执行器名称',
    hostname VARCHAR(255) NOT NULL COMMENT '服务器主机名',
    host_ip VARCHAR(64) NOT NULL COMMENT '服务器 IP，兼容 IPv4 和 IPv6',
    executor_port INT NOT NULL COMMENT '执行器服务端口',
    os_name VARCHAR(128) DEFAULT NULL COMMENT '操作系统信息',
    executor_status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '在线状态：ONLINE/OFFLINE',
    management_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '管理状态：ACTIVE/INACTIVE',
    token_ciphertext VARCHAR(512) DEFAULT NULL COMMENT '执行器认证 Token 密文',
    last_heartbeat_time TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次收到心跳的时间',
    last_report_time TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次收到状态上报的时间',
    register_time TIMESTAMP NULL DEFAULT NULL COMMENT '首次注册成功时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_executor_info_executor_id (executor_id),
    UNIQUE KEY uk_executor_info_executor_name (executor_name),
    KEY idx_executor_info_status_heartbeat (executor_status, last_heartbeat_time),
    KEY idx_executor_info_host_ip (host_ip)
) ENGINE=InnoDB COMMENT='执行器信息表';

-- 每台执行器当前生效的基准配置快照。
-- 配置正文加密保存；故障恢复时使用平台保存的摘要校验服务器备份文件。
CREATE TABLE IF NOT EXISTS executor_config_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    executor_id VARCHAR(64) NOT NULL COMMENT '所属执行器编号',
    redis_config_ciphertext LONGTEXT NOT NULL COMMENT 'Redis配置文件密文',
    redis_config_sha256 CHAR(64) NOT NULL COMMENT 'Redis配置文件SHA-256',
    sentinel_config_ciphertext LONGTEXT NOT NULL COMMENT 'Sentinel配置文件密文',
    sentinel_config_sha256 CHAR(64) NOT NULL COMMENT 'Sentinel配置文件SHA-256',
    snapshot_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    initialized_time TIMESTAMP NOT NULL COMMENT '首次建立基准配置的时间',
    last_verified_time TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次与服务器备份校验一致的时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_snapshot_executor_id (executor_id),
    KEY idx_config_snapshot_status (snapshot_status)
) ENGINE=InnoDB COMMENT='执行器基准配置快照表';

-- 平台上的一条完整业务任务，例如故障恢复、版本升级或服务器切换。
CREATE TABLE IF NOT EXISTS operation_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no VARCHAR(32) NOT NULL COMMENT '业务任务编号',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型：FAULT_RECOVERY/VERSION_UPGRADE/SERVER_SWITCH',
    task_status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/RUNNING/SUCCESS/FAILED/ROLLING_BACK/ROLLED_BACK',
    request_params JSON DEFAULT NULL COMMENT '任务级请求参数',
    failure_reason VARCHAR(1000) DEFAULT NULL COMMENT '任务失败原因',
    created_by VARCHAR(64) NOT NULL COMMENT '任务创建人',
    operation_reason VARCHAR(500) NOT NULL COMMENT '执行运维任务的原因',
    start_time TIMESTAMP NULL DEFAULT NULL COMMENT '任务开始时间',
    end_time TIMESTAMP NULL DEFAULT NULL COMMENT '任务结束时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_task_task_no (task_no),
    KEY idx_operation_task_type_status (task_type, task_status),
    KEY idx_operation_task_create_time (create_time)
) ENGINE=InnoDB COMMENT='运维业务任务表';

-- 业务任务分解后的执行步骤。同一个 step_no 可以并行下发给多台执行器。
CREATE TABLE IF NOT EXISTS operation_task_step (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no VARCHAR(32) NOT NULL COMMENT '所属业务任务编号',
    step_no INT NOT NULL COMMENT '步骤序号，从 1 开始',
    executor_id VARCHAR(64) NOT NULL COMMENT '负责执行该步骤的执行器编号',
    operation_type VARCHAR(64) NOT NULL COMMENT '执行器原子操作类型',
    step_status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/DISPATCHED/SUCCESS/FAILED/TIMEOUT/CANCELLED',
    request_params JSON DEFAULT NULL COMMENT '下发给执行器的操作参数',
    result_json JSON DEFAULT NULL COMMENT '执行器返回的完整结果',
    error_code VARCHAR(64) DEFAULT NULL COMMENT '执行器返回的错误码',
    failure_reason VARCHAR(1000) DEFAULT NULL COMMENT '步骤失败原因',
    dispatch_count INT NOT NULL DEFAULT 0 COMMENT '已下发次数',
    last_dispatch_time TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次下发时间',
    start_time TIMESTAMP NULL DEFAULT NULL COMMENT '步骤开始时间',
    end_time TIMESTAMP NULL DEFAULT NULL COMMENT '步骤结束时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_step_executor (task_no, step_no, executor_id),
    KEY idx_task_step_schedule (task_no, step_no, step_status),
    KEY idx_task_step_executor_status (executor_id, step_status)
) ENGINE=InnoDB COMMENT='运维任务执行步骤表';

-- 按日期生成 TASKyyyyMMdd0001 格式的任务编号。
CREATE TABLE IF NOT EXISTS task_no_sequence (
    biz_date DATE NOT NULL COMMENT '任务编号所属日期',
    current_value INT NOT NULL DEFAULT 0 COMMENT '当天已经分配到的最大流水号',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (biz_date)
) ENGINE=InnoDB COMMENT='任务编号每日流水表';

-- 平台接收到的 Redis 版本包元数据，安装包文件本身保存在磁盘上。
CREATE TABLE IF NOT EXISTS redis_version_package (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    package_name VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
    declared_version VARCHAR(64) NOT NULL COMMENT '用户填写或文件名声明的版本号',
    detected_version VARCHAR(64) DEFAULT NULL COMMENT '解包校验后识别出的真实版本号',
    storage_path VARCHAR(1000) NOT NULL COMMENT '安装包在平台服务器上的存储路径',
    package_hash CHAR(64) NOT NULL COMMENT '安装包 SHA-256',
    package_size BIGINT NOT NULL COMMENT '安装包字节数',
    verify_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '校验状态：PENDING/PASSED/FAILED',
    verify_message VARCHAR(1000) DEFAULT NULL COMMENT '版本包校验说明',
    uploaded_by VARCHAR(64) NOT NULL COMMENT '上传人',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_package_hash (package_hash),
    UNIQUE KEY uk_version_package_declared_version (declared_version),
    KEY idx_version_package_version (declared_version, detected_version),
    KEY idx_version_package_status (verify_status)
) ENGINE=InnoDB COMMENT='Redis 版本包表';




-- 每台执行器最近一次上报的Redis和Sentinel运行状态。
CREATE TABLE IF NOT EXISTS executor_runtime_status (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    executor_id VARCHAR(64) NOT NULL COMMENT '执行器唯一编号',
    redis_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'Redis状态：UP/DOWN/UNKNOWN',
    redis_role VARCHAR(16) DEFAULT NULL COMMENT 'Redis角色：MASTER/REPLICA',
    redis_version VARCHAR(64) DEFAULT NULL COMMENT 'Redis实际版本',
    redis_master_host VARCHAR(64) DEFAULT NULL COMMENT '副本当前连接的主节点地址',
    redis_master_port INT DEFAULT NULL COMMENT '副本当前连接的主节点端口',
    redis_error_message VARCHAR(1000) DEFAULT NULL COMMENT 'Redis采集失败原因',
    sentinel_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'Sentinel状态：UP/DOWN/UNKNOWN',
    sentinel_master_name VARCHAR(128) DEFAULT NULL COMMENT 'Sentinel监控的主节点名称',
    sentinel_master_host VARCHAR(64) DEFAULT NULL COMMENT 'Sentinel记录的主节点地址',
    sentinel_master_port INT DEFAULT NULL COMMENT 'Sentinel记录的主节点端口',
    sentinel_error_message VARCHAR(1000) DEFAULT NULL COMMENT 'Sentinel检查失败原因',
    collect_time BIGINT NOT NULL COMMENT '执行器采集时间戳（毫秒）',
    received_time TIMESTAMP NOT NULL COMMENT '平台接收时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_runtime_status_executor_id (executor_id),
    KEY idx_runtime_status_received_time (received_time)
) ENGINE=InnoDB COMMENT='执行器最新运行状态表';