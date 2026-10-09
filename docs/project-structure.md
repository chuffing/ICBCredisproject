# Redis运维平台项目结构

平台采用“先按业务功能分包，再在业务内按职责分层”的结构，与执行器项目的组织方式保持一致。

```text
com.redisops.platform
├─ agent
│  ├─ config                 执行器相关平台配置
│  ├─ controller             注册、心跳接口
│  ├─ model                  注册和心跳请求/响应
│  ├─ entity                 executor_info实体
│  ├─ mapper                 执行器数据访问
│  ├─ service/impl           注册、认证、心跳业务
│  ├─ task                   60秒离线检查
│  └─ configsnapshot
│     ├─ controller          配置快照上传和查询接口
│     ├─ model               快照请求/响应
│     ├─ entity/mapper       配置快照持久化
│     └─ service/impl        首次保存、后续只校验
├─ operation
│  ├─ controller             通用任务创建、查询、结果回报
│  ├─ model                  任务和步骤请求/响应
│  ├─ entity/mapper          operation_task及step持久化
│  ├─ service/impl           步骤屏障、下发和结果推进
│  ├─ client                 平台调用执行器
│  ├─ type                   业务任务和原子操作枚举
│  └─ config                 任务推进调度器
├─ recovery
│  ├─ controller             故障恢复业务接口
│  ├─ model                  故障恢复业务参数
│  └─ service/impl           六阶段拓扑恢复编排
├─ versionupgrade
│  ├─ controller             版本包上传、查询和版本升级任务接口
│  ├─ model                  版本包返回数据和三节点升级参数
│  ├─ entity/mapper          redis_version_package持久化
│  ├─ config                 版本包目录和大小上限
│  ├─ exception              同版本不同内容等业务冲突
│  └─ service/impl           版本包校验、七阶段升级和失败回滚编排
├─ security
│  └─ service/impl           Token和配置正文加解密
├─ common
│  ├─ model                  统一API响应
│  └─ controller             开发检查接口
├─ config                    全局HTTP客户端配置
└─ RedisPlatformApplication  平台启动类
```

## 查找一项业务的方法

查看故障恢复时，从 `recovery/controller/FaultRecoveryController` 开始，继续看 `recovery/service/FaultRecoveryService` 和 `recovery/service/impl/FaultRecoveryServiceImpl`。它生成通用步骤后，再进入 `operation/service/impl/OperationTaskServiceImpl`。

查看执行器注册和心跳时，进入 `agent`。查看配置备份上传及摘要校验时，进入 `agent/configsnapshot`。

查看任务为什么进入下一阶段、为什么失败或如何下发执行器时，进入 `operation`。

查看版本包为什么被接受或拒绝时，从 `versionupgrade/controller/VersionPackageController`
进入 `VersionPackageServiceImpl`。查看三节点版本升级步骤时，从
`VersionUpgradeController` 进入 `VersionUpgradeServiceImpl`；失败补偿步骤由
`VersionUpgradeRollbackPlanner` 生成，再交回通用调度器执行。

## 分层含义

- `controller`：接收HTTP请求并返回响应。
- `model`：接口请求和响应的数据结构。
- `entity`：与数据库表对应的对象。
- `mapper`：执行数据库增删改查。
- `service`：业务能力接口。
- `service/impl`：业务能力的具体实现。
- `client`：调用其他进程的HTTP客户端。
- `task`：定时任务。
- `type`：固定枚举和类型定义。
