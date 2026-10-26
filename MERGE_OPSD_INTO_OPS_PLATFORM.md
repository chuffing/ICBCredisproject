# opsD 合并到 codex/ops-platform 说明

## 合并范围

- 源分支：`opsD` (`45f6a7d`)
- 目标分支：`codex/ops-platform` (`4fa409e`)
- 共同祖先：`d55eeef`
- 合并日期：2026-10-26

## 已合入功能

- 执行器批量纳管/取消纳管，并同步绑定 Redis 实例状态。
- 执行器到平台的 `/executor/ping` 连通性探测。
- Sentinel 节点随指标上报、参数校验、状态更新和离线判定。
- 心跳、指标和纳管状态变化后的相关缓存失效。
- 执行器重复注册时复用已有 Redis 实例，兼容未绑定字段为 `NULL` 或空串。
- 监控、告警、审计及测试数据的相关调整。
- `BaseResponse` 补充 getter，使注册响应能正确序列化 `data`，并更新对应契约测试。

## 冲突处理

1. `RedisInstance` 保留运维平台分支的实体结构和可空 `configSha256`。
2. `SentinelNodeInfo` 保留 `configSha256`，不恢复已在 `4fa409e` 删除的 master 冗余字段。
3. `application.yml` 同时保留运维任务配置和 opsD 的实例/Sentinel 独立离线阈值。
4. 不恢复已废弃的 `schema.sql`；存量库调整写入 `005_opsd_monitoring_management.sql`。
5. 从 Git 中移除 `target/` 编译产物，并通过 `.gitignore` 防止再次提交。
6. 不合入 `opsD` 的 `SecurityConfig`：当前项目未引入 Spring Security，且该配置为全量 `permitAll`；继续使用现有 `WebConfig` 处理 CORS。

## 配置变更

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `OFFLINE_THRESHOLD_SECONDS` | `70` | Redis 实例离线判定阈值 |
| `SENTINEL_OFFLINE_THRESHOLD_SECONDS` | `330` | Sentinel 离线判定阈值 |

## 数据库升级

存量 PostgreSQL 环境需按顺序执行 `src/main/resources/db/postgresql/` 中的增量迁移，其中本次新增：

- `005_opsd_monitoring_management.sql`

该脚本增加 `last_report_time` 及执行器、Redis 实例、Sentinel、告警相关唯一约束和查询索引。

## 验证要求

- 执行 Maven 全量测试。
- 确认编译后 `target/` 不再出现于 Git 变更中。
- 在测试环境验证执行器注册、纳管/取消纳管、指标上报和 Sentinel 列表。
- 若后续需要身份认证，应单独设计 Spring Security 依赖、认证规则和 CORS 边界，不建议使用全量 `permitAll`。

## 验证结果

- `mvn test`：通过。
- 测试总数：23。
- 失败：0。
- 错误：0。
- 跳过：0。
