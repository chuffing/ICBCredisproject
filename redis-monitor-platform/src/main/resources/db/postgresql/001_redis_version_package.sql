CREATE TABLE IF NOT EXISTS redis_version_package (
    id BIGSERIAL PRIMARY KEY,
    package_name VARCHAR(128) NOT NULL,
    redis_version VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    package_size BIGINT NOT NULL,
    package_status VARCHAR(16) NOT NULL,
    uploaded_by VARCHAR(64) NOT NULL,
    created_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_redis_version_package_version UNIQUE (redis_version),
    CONSTRAINT uk_redis_version_package_sha256 UNIQUE (sha256),
    CONSTRAINT ck_redis_version_package_status
        CHECK (package_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_redis_version_package_size CHECK (package_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_redis_version_package_created_time
    ON redis_version_package (created_time DESC);

COMMENT ON TABLE redis_version_package IS 'Redis源码版本安装包';
COMMENT ON COLUMN redis_version_package.storage_path IS
    '平台服务器内部存储路径，不通过接口返回';
COMMENT ON COLUMN redis_version_package.package_status IS
    'ENABLED可用于新升级任务，DISABLED不可用于新升级任务';
