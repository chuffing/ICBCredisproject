-- 运维步骤错误码统一改为整数；执行前应先清空测试数据库中的业务数据。
ALTER TABLE operation_task_step
    ALTER COLUMN error_code DROP DEFAULT,
    ALTER COLUMN error_code DROP NOT NULL,
    ALTER COLUMN error_code TYPE INTEGER
        USING NULLIF(error_code, '')::INTEGER;
