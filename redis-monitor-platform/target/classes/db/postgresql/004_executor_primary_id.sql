-- 执行器统一使用 executor_info.id 作为执行器编号，删除重复的业务编号字段。
ALTER TABLE executor_info
    DROP COLUMN IF EXISTS executor_id;
