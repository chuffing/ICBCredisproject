ALTER TABLE sentinel_node_info
    ADD COLUMN IF NOT EXISTS master_host_ip VARCHAR(64),
    ADD COLUMN IF NOT EXISTS master_redis_port VARCHAR(16);

COMMENT ON COLUMN sentinel_node_info.master_host_ip IS
    'Sentinel当前监控的Redis主节点IP';
COMMENT ON COLUMN sentinel_node_info.master_redis_port IS
    'Sentinel当前监控的Redis主节点端口';

-- 注册时先创建未纳管记录，首次纳管时再保存平台配置摘要。
ALTER TABLE redis_instance
    ALTER COLUMN config_sha256 DROP NOT NULL;

ALTER TABLE sentinel_node_info
    ALTER COLUMN config_sha256 DROP NOT NULL;
