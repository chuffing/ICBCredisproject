# 三节点服务器切换

服务器切换用于把旧三节点 Redis + Sentinel 拓扑切换到三台已经完成基础环境准备、已经部署 Agent 的新服务器。平台负责部署新版本和配置；为避免新旧 Sentinel 使用同一个 MASTER 名称时发生集群串联，切流阶段必须在启动任何新 Redis 前停止旧三台 Sentinel。

## 前置条件

1. 新旧六台服务器的 Agent 都已注册并持续上报心跳。
2. 三台新服务器的基础目录、权限、systemd 单元和 Agent 脚本已经准备完成。
3. 三台新服务器均未激活 Redis 版本，`/opt/redis/current` 不存在，Redis 和 Sentinel 均停止。
4. 平台已有通过校验的 Redis 版本包。
5. 平台已为三台新执行器保存各自的 Redis 和 Sentinel 基准配置；配置中的主从地址和 Sentinel 监控地址必须属于新拓扑。
6. 请求同时声明三台旧拓扑执行器。平台会在切流点停止其 Sentinel；旧 Redis 暂时保留，便于后续人工确认或回退。

## 创建任务接口

```http
POST /api/server-switch-tasks
Content-Type: application/json
```

```json
{
  "versionPackageId": 1,
  "masterExecutorId": "AGENT-NEW-01",
  "redisPort": 6379,
  "createdBy": "admin",
  "operationReason": "三台Redis服务器硬件更换",
  "nodes": [
    {"executorId": "AGENT-NEW-01"},
    {"executorId": "AGENT-NEW-02"},
    {"executorId": "AGENT-NEW-03"}
  ],
  "sourceNodes": [
    {"executorId": "AGENT-OLD-01"},
    {"executorId": "AGENT-OLD-02"},
    {"executorId": "AGENT-OLD-03"}
  ]
}
```

`nodes` 是三台新服务器，`sourceNodes` 是当前旧拓扑。新旧执行器不能重复。平台读取数据库中已经校验的版本包和新节点加密配置快照，调用方不能自行传入摘要。

## 执行步骤

| stepNo | 操作 | 目标 | 节点数 | 目的 |
| --- | --- | --- | ---: | --- |
| 1 | `PRECHECK_HOST` | 新节点 | 3 | 检查目录、权限、脚本、服务、端口和 current 链接 |
| 2 | `DEPLOY_VERSION` | 新节点 | 3 | 下发并校验 Redis 版本包 |
| 3 | `STOP_SENTINEL` | 新节点 | 3 | 保证新机 Sentinel 停止 |
| 4 | `STOP_REDIS` | 新节点 | 3 | 保证新机 Redis 停止 |
| 5 | `DEPLOY_CONFIG` | 新节点 | 3 | 解密平台配置、复核摘要并通过 multipart 下发 |
| 6 | `SWITCH_VERSION` | 新节点 | 3 | 创建 current 链接并复核实际二进制版本 |
| 7 | `STOP_SENTINEL` | 旧节点 | 3 | 在任何新 Redis 启动前隔离旧 Sentinel，避免同名集群串联 |
| 8 | `START_REDIS` | 新主节点 | 1 | 启动并连续确认指定节点为 MASTER |
| 9 | `START_REDIS` | 新副本 | 2 | 启动两个副本并确认复制源、端口和链路 |
| 10 | `START_SENTINEL` | 新节点 | 3 | 启动 Sentinel，连续确认监控地址就是新主库 |

同一个 stepNo 内可以并行；下一组必须等待上一组全部成功。完整计划共 10 组、27 条节点操作。

## 为什么第 7 组必须提前

旧 Sentinel 会记住历史上见过的副本。即使新服务器上的配置把某节点设为新主库，只要旧 Sentinel 仍运行，它就可能在新 Redis 启动后把该节点重新配置成旧主库的副本。停止旧 Sentinel 如果晚于启动新 Redis，平台可能先看到短暂正确状态，随后拓扑再发生变化。因此必须先停止旧 Sentinel，再启动新 Redis。

`START_SENTINEL` 还会读取 `expectedMasterHost` 和 `expectedMasterPort`，要求健康结果连续三次指向新主库才成功，避免只检查 PING 而误报。

## 配置文件下发

`DEPLOY_CONFIG` 从 `executor_config_snapshot` 读取 ACTIVE 快照，使用 AES-GCM 解密配置正文，重新计算 SHA-256 并与数据库和任务步骤摘要比较，最后通过 `/api/agent/config-task` 发送任务 JSON、`redis.conf` 和 `sentinel.conf`。配置正文不写入运维任务表，也不通过普通查询接口返回。

## 失败与回滚

- 第 1 至第 6 组失败：旧 Sentinel 尚未停止，新拓扑服务未启动，任务直接失败。
- 第 7 至第 10 组失败：平台先停止三台新 Sentinel，再停止三台新 Redis，随后幂等启动旧三台 Sentinel。
- 补偿全部成功后任务状态为 `ROLLED_BACK`；补偿失败时为 `ROLLBACK_FAILED`，需要人工介入。
- 首次部署的新服务器没有旧版本可恢复，不调用 `ROLLBACK_VERSION`。

## 2026-10-08 本地验证

任务 `TASK202610080009` 在六台桥接网络虚拟机上完成 27/27 步骤。新拓扑稳定复查结果：`172.20.10.5` 为 MASTER，`172.20.10.4` 与 `172.20.10.6` 为其 REPLICA，三台 Sentinel 均监控 `172.20.10.5:6379`，`CKQUORUM` 返回 3 个可用 Sentinel。旧三台 Sentinel 均处于 inactive。