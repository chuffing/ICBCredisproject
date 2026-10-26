# 数据库表设计

## operation_task

| 字段名 | PostgreSQL类型 | 允许为空 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | 否 | 自增 | 主键 | 内部主键 |
| task_no | VARCHAR(32) | 否 | 无 | 唯一 | 运维任务编号，格式为TASK加12位数字 |
| task_type | VARCHAR(32) | 否 | 无 |  | 任务类型：FAULT_RECOVERY、VERSION_UPGRADE、SERVER_SWITCH |
| task_status | VARCHAR(32) | 否 | CREATED |  | 任务状态：CREATED、RUNNING、RESULT_UNKNOWN、MANUALLY_TERMINATED、SUCCESS、FAILED |
| operation_reason | VARCHAR(500) | 否 | 无 |  | 操作原因 |
| created_by | VARCHAR(64) | 否 | 无 |  | 操作人 |
| request_params | JSONB | 是 | NULL |  | 创建任务时保存的环境和业务参数快照 |
| failure_reason | VARCHAR(1000) | 是 | NULL |  | 任务失败原因 |
| termination_reason | VARCHAR(500) | 是 | NULL |  | 人工结束任务的原因 |
| terminated_by | VARCHAR(64) | 是 | NULL |  | 人工结束任务的操作人 |
| terminated_time | TIMESTAMP | 是 | NULL |  | 人工结束任务的时间 |
| start_time | TIMESTAMP | 是 | NULL |  | 任务开始时间 |
| end_time | TIMESTAMP | 是 | NULL |  | 任务结束时间 |
| created_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 更新时间 |

## operation_task_step

| 字段名 | PostgreSQL类型 | 允许为空 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT | 否 | 自增 | 主键 | 内部主键 |
| task_no | VARCHAR(32) | 否 | 无 | 外键关联operation_task.task_no | 所属任务编号 |
| step_no | INTEGER | 否 | 无 | 大于0 | 阶段编号，同一阶段可以包含多台执行器的步骤 |
| executor_id | VARCHAR(64) | 否 | 无 |  | 目标执行器编号 |
| operation_type | VARCHAR(64) | 否 | 无 |  | 执行器原子操作类型 |
| step_status | VARCHAR(32) | 否 | CREATED |  | 步骤状态：CREATED、DISPATCHING、DISPATCHED、RESULT_UNKNOWN、SUCCESS、FAILED |
| request_params | JSONB | 是 | NULL |  | 下发给执行器的任务参数 |
| result_json | JSONB | 是 | NULL |  | 执行器返回的完整结果 |
| error_code | VARCHAR(64) | 是 | NULL |  | 执行器返回的错误码 |
| failure_reason | VARCHAR(1000) | 是 | NULL |  | 步骤失败原因 |
| dispatch_time | TIMESTAMP | 是 | NULL |  | 任务下发时间 |
| start_time | TIMESTAMP | 是 | NULL |  | 步骤开始时间 |
| end_time | TIMESTAMP | 是 | NULL |  | 步骤结束时间 |
| created_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 更新时间 |

| 联合约束 | 字段 | 说明 |
| --- | --- | --- |
| 唯一约束 | task_no、step_no、executor_id | 同一任务、同一阶段、同一执行器只能存在一个步骤 |

## task_no_sequence

| 字段名 | PostgreSQL类型 | 允许为空 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- | --- | --- |
| biz_date | DATE | 否 | 无 | 主键 | 任务编号所属业务日期 |
| current_value | INTEGER | 否 | 0 | 大于等于0 | 当天已经分配的最大流水号 |
| created_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 更新时间 |

## redis_instance

| 字段名 | PostgreSQL类型 | 允许为空 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- | --- | --- |
| id | VARCHAR(64) | 否 | 无 | 主键 | 内部主键 |
| instance_id | VARCHAR(64) | 否 | 无 | 唯一 | Redis实例编号 |
| instance_name | VARCHAR(128) | 否 | 无 |  | Redis实例名称 |
| host_ip | VARCHAR(64) | 否 | 无 |  | Redis所在服务器IP |
| redis_port | VARCHAR(16) | 否 | 无 |  | Redis服务端口 |
| role | VARCHAR(16) | 否 | 无 |  | Redis角色：MASTER、REPLICA |
| redis_version | VARCHAR(64) | 是 | NULL |  | Redis版本 |
| run_status | VARCHAR(16) | 否 | 无 |  | Redis运行状态 |
| master_host_ip | VARCHAR(64) | 是 | NULL |  | 当前主节点IP，MASTER节点为空 |
| master_redis_port | VARCHAR(16) | 是 | NULL |  | 当前主节点Redis端口，MASTER节点为空 |
| config_sha256 | VARCHAR(64) | 是 | NULL | 64位十六进制字符串 | 未纳管时可为空，正式纳管前保存Redis配置文件摘要 |
| last_heartbeat_time | TIMESTAMP | 是 | NULL |  | 最近一次心跳时间 |
| management_status | VARCHAR(16) | 否 | 1 |  | 纳管状态：0表示未纳管，1表示已纳管 |
| created_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 更新时间 |

| 联合约束 | 字段 | 说明 |
| --- | --- | --- |
| 唯一约束 | host_ip、redis_port | 同一个Redis地址只能存在一条实例记录 |

## sentinel_node_info

| 字段名 | PostgreSQL类型 | 允许为空 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- | --- | --- |
| id | VARCHAR(128) | 否 | 无 | 主键 | Sentinel节点编号，使用host:port |
| node_name | VARCHAR(128) | 否 | 无 |  | Sentinel节点名称 |
| node_ip | VARCHAR(64) | 否 | 无 |  | Sentinel所在服务器IP |
| node_port | VARCHAR(16) | 否 | 无 |  | Sentinel服务端口 |
| sentinel_status | VARCHAR(16) | 否 | 无 |  | Sentinel运行状态 |
| config_sha256 | VARCHAR(64) | 是 | NULL | 64位十六进制字符串 | 未纳管时可为空，正式纳管前保存Sentinel配置文件摘要 |
| last_heartbeat_time | TIMESTAMP | 是 | NULL |  | 最近一次心跳时间 |
| management_status | VARCHAR(16) | 否 | 1 |  | 纳管状态：0表示未纳管，1表示已纳管 |
| created_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_time | TIMESTAMP | 否 | CURRENT_TIMESTAMP |  | 更新时间 |

| 联合约束 | 字段 | 说明 |
| --- | --- | --- |
| 唯一约束 | node_ip、node_port | 同一个Sentinel地址只能存在一条节点记录 |

## 数据库结构变更记录

| 日期 | 数据表 | 变更内容 | 用途 |
| --- | --- | --- | --- |
| 2026-10-19 | operation_task | 新增termination_reason、terminated_by、terminated_time字段，并新增MANUALLY_TERMINATED任务状态 | 保存人工结束任务的审计信息，并阻止旧任务继续调度 |
| 2026-10-20 | redis_instance、sentinel_node_info | 新增config_sha256字段 | 故障恢复下发时向执行器提供两份备份配置文件的SHA256摘要 |
| 2026-10-22 | sentinel_node_info | 新增master_host_ip、master_redis_port字段 | 保存注册时采集的Sentinel监控主节点，用于首次纳管校验 |
| 2026-10-22 | redis_instance、sentinel_node_info | config_sha256改为可空 | 允许注册阶段先创建未纳管记录，正式纳管前再保存配置摘要 |
| 2026-10-26 | sentinel_node_info | 删除master_host_ip、master_redis_port字段 | 当前拓扑通过Redis实例关系和实时状态确定，不再重复保存Sentinel监控目标 |

## 待完善事项

### 任务结果上报接口必填参数处理

当前`OperationTaskController.reportTaskResult`中的`@RequestHeader`和
`@RequestBody`暂时设置了`required = false`，目的是让缺少认证请求头或
请求体的请求进入Controller，由现有手动校验返回明确的业务错误。

后续需要完成以下调整：

1. 删除两个注解中的`required = false`，恢复Spring默认的必填语义。
2. 在`GlobalExceptionHandler`中处理`MissingRequestHeaderException`，
   将缺少Authorization请求头转换为执行器认证错误。
3. 在`GlobalExceptionHandler`中处理`HttpMessageNotReadableException`，
   将缺少请求体或请求体无法解析转换为请求参数错误。
4. 调整完成后确认接口不会把上述请求错误错误地返回为`SYSTEM_ERROR`。
