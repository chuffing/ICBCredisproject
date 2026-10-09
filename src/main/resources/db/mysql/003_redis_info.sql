-- Redis正式纳管实例表。
-- 服务器切换目标在任务成功前只保存在operation_task.request_params中；
-- 只有整个新拓扑验收成功后，才把旧记录标记为REMOVED并写入新的MANAGED记录。

USE redis_operation_platform;

CREATE TABLE IF NOT EXISTS redis_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    executor_id VARCHAR(64) NOT NULL COMMENT '所在服务器的执行器编号',
    host_ip VARCHAR(64) NOT NULL COMMENT 'Redis所在服务器IP',
    redis_port INT NOT NULL DEFAULT 6379 COMMENT 'Redis端口',
    sentinel_port INT NOT NULL DEFAULT 26379 COMMENT 'Sentinel端口',
    redis_role VARCHAR(16) NOT NULL COMMENT '正式拓扑角色：MASTER/REPLICA',
    redis_version VARCHAR(64) DEFAULT NULL COMMENT '最近一次运维验收确认的Redis版本',
    master_host VARCHAR(64) NOT NULL COMMENT '正式拓扑MASTER地址',
    master_port INT NOT NULL DEFAULT 6379 COMMENT '正式拓扑MASTER端口',
    redis_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '最近一次运维验收状态：UP/DOWN/UNKNOWN',
    sentinel_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '最近一次运维验收状态：UP/DOWN/UNKNOWN',
    redis_password_ciphertext VARCHAR(512) NOT NULL COMMENT 'AES-GCM加密后的Redis密码，禁止返回前端',
    management_status VARCHAR(16) NOT NULL DEFAULT 'MANAGED' COMMENT '纳管状态：MANAGED/REMOVED',
    last_check_time TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次运维实时验收时间',
    managed_time TIMESTAMP NOT NULL COMMENT '最近一次正式纳管时间',
    removed_time TIMESTAMP NULL DEFAULT NULL COMMENT '退出正式纳管时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_redis_info_executor_id (executor_id),
    KEY idx_redis_info_management_status (management_status),
    KEY idx_redis_info_host_ip (host_ip)
) ENGINE=InnoDB COMMENT='Redis正式纳管实例信息表';
