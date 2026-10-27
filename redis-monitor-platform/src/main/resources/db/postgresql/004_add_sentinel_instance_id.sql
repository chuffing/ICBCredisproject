-- SentinelNodeInfo实体包含instanceId，数据库需要保留对应字段。
ALTER TABLE sentinel_node_info
    ADD COLUMN IF NOT EXISTS instance_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sentinel_instance_id
    ON sentinel_node_info (instance_id);
