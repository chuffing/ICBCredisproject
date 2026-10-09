-- 已经执行过001脚本的开发数据库使用此增量脚本。
-- 一个Redis版本号只能绑定一个不可变安装包，重复执行本脚本不会重复创建索引。

USE redis_operation_platform;

SET @version_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'redis_version_package'
      AND index_name = 'uk_version_package_declared_version'
);

SET @version_index_sql = IF(
    @version_index_exists = 0,
    'ALTER TABLE redis_version_package ADD UNIQUE KEY uk_version_package_declared_version (declared_version)',
    'SELECT ''uk_version_package_declared_version already exists'''
);

PREPARE version_index_statement FROM @version_index_sql;
EXECUTE version_index_statement;
DEALLOCATE PREPARE version_index_statement;
