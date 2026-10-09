# Redis三节点版本升级

## 接口

上传版本包：

```text
POST /api/version-packages
Content-Type: multipart/form-data

version=7.2.5
uploadedBy=admin
file=<redis-7.2.5.tar.gz>
```

查询版本包：

```text
GET /api/version-packages
```

创建升级任务：

```text
POST /api/version-upgrade-tasks
Content-Type: application/json
```

```json
{
  "versionPackageId": 1,
  "masterExecutorId": "AGENT-14377D6F148041A6",
  "redisPort": 6379,
  "createdBy": "admin",
  "operationReason": "Redis版本升级",
  "nodes": [
    {"executorId": "AGENT-14377D6F148041A6"},
    {"executorId": "AGENT-B22D2F9F95504132"},
    {"executorId": "AGENT-210DB9E37A8049CE"}
  ]
}
```

## 正常升级步骤

1. 三台执行器接收并部署版本包，不影响正在运行的服务。
2. 三台服务器停止Sentinel，冻结自动故障转移。
3. 三台服务器停止Redis。
4. 三台服务器把`current`软链接切换到目标版本。
5. 启动指定主Redis并验证MASTER角色。
6. 启动两个从Redis并验证复制源和链路状态。
7. 启动三台Sentinel，连续验证它们实际监控的主库地址和端口仍然正确。

三台服务器共生成18条正常步骤。同一编号的步骤可以在不同服务器上同时执行，
下一编号必须等待本组全部成功。

## 失败处理

- 第1阶段失败：线上服务尚未受影响，任务直接失败，不执行回滚。
- 第2阶段失败：重新启动三台Sentinel。
- 第3阶段失败：按主、从、Sentinel顺序恢复旧拓扑。
- 第4阶段失败：只对已经切换成功的节点执行`ROLLBACK_VERSION`，然后恢复旧拓扑。
- 第5至7阶段失败：停止新版本拓扑，回滚三台已切换节点，再恢复旧拓扑。

开始补偿后任务状态为`ROLLING_BACK`。补偿全部成功后状态为`ROLLED_BACK`，表示
升级失败但旧版本拓扑已经恢复；补偿本身失败时状态为`FAILED`，需要人工介入。

## 数据库增量脚本

已经执行过初始建表脚本的环境，需要执行：

```text
src/main/resources/db/mysql/002_version_upgrade_constraints.sql
```

该脚本为`declared_version`增加唯一索引，保证同一个版本号不能绑定不同内容。

## 2026-10-08 实机验证

- `TASK202610080011`：Redis 6.2.18 升级到 6.2.19，18/18 步骤成功。
- `TASK202610080013`：再次使用内容完全相同的 6.2.19 包，验证版本目录幂等复用，18/18 步骤成功。
- 三台服务器的 `current` 均指向 `/opt/redis/releases/6.2.19`，实际 `redis-cli` 版本均为 6.2.19。
- 主节点为 `172.20.10.5`，两个副本的复制链路均为 `up`。
- 第 7 组任务参数包含 `expectedMasterHost=172.20.10.5` 和 `expectedMasterPort=6379`，三台 Sentinel 均校验通过。