# 三节点故障恢复

故障恢复使用每台服务器备份磁盘中的 Redis 和 Sentinel 配置，执行前先与平台保存的加密配置快照进行 SHA-256 校验。只有两份配置均与平台基准一致，执行器才会恢复配置。

## 接口

```http
POST /api/fault-recovery-tasks
Content-Type: application/json
```

```json
{
  "createdBy": "admin",
  "operationReason": "配置损坏后恢复整个拓扑",
  "masterExecutorId": "AGENT-NEW-01",
  "redisPort": 6379,
  "nodes": [
    {"executorId": "AGENT-NEW-01"},
    {"executorId": "AGENT-NEW-02"},
    {"executorId": "AGENT-NEW-03"}
  ]
}
```

## 执行步骤

1. 停止三台 Sentinel。
2. 停止三台 Redis。
3. 校验本机备份配置与平台配置摘要，然后恢复 Redis 和 Sentinel 配置。
4. 启动指定主节点并连续确认其为 MASTER。
5. 启动两个副本，确认主库地址、端口和复制链路。
6. 启动三台 Sentinel，连续确认实际监控地址和端口就是恢复后的主节点。

完整任务共 6 组、15 条节点操作。同组并行，下一组等待上一组全部成功。

## 2026-10-08 实机验证

- `TASK202610080010`：首次完整恢复，15/15 步骤成功。
- `TASK202610080012`：增加 Sentinel 目标主库强校验后再次恢复，15/15 步骤成功。
- 恢复后 `172.20.10.5` 为 MASTER，`172.20.10.4` 和 `172.20.10.6` 为 REPLICA，复制链路均为 `up`。
- 三台 Sentinel 均监控 `172.20.10.5:6379`，`CKQUORUM` 返回 3 个可用 Sentinel。
- 故障恢复不会切换 Redis 安装版本；验证时三台仍运行 6.2.19。