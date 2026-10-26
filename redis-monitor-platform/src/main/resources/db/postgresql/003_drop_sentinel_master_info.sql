-- Sentinel监控目标以实时拓扑和Redis实例主从关系为准，删除不再使用的冗余字段。
ALTER TABLE sentinel_node_info
    DROP COLUMN IF EXISTS master_host_ip,
    DROP COLUMN IF EXISTS master_redis_port;
