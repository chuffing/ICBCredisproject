ALTER TABLE operation_task_step
    RENAME COLUMN last_dispatch_time TO dispatch_time;

COMMENT ON COLUMN operation_task_step.dispatch_time IS
    '任务下发时间';
