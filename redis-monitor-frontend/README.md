# Redis 监控平台 · 前端

基于 Vue 2 + Element UI + ECharts 的 Redis 监控平台前端，接口完全对齐 `redis-monitor-platform`（平台端后端）。
执行器端运行在生产服务器上，自动向平台注册；注册/绑定成功后每 30 秒上报心跳，每 5 分钟将 Redis 指标、Sentinel 状态和 ping 平台延迟一次上报；
本前端只访问平台端 HTTP 接口，不直接访问执行器。

## 运行与构建

```bash
npm ci --ignore-scripts
npm run serve     # 开发环境，http://localhost:8080
npm run build     # 生产构建，产物在 dist/
```

> 本项目基于 Vue CLI 4（webpack 4）。如果使用 Node 17+ 构建出现
> `ERR_OSSL_EVP_UNSUPPORTED`，先设置 `NODE_OPTIONS=--openssl-legacy-provider` 再执行构建脚本。
> `vue.config.js` 已设置 `parallel: false`，避免受限构建环境/容器中 Terser fork 失败（`spawn EPERM`）。

开发环境通过 `vue.config.js` 中的 `devServer.proxy` 把 `/monitor`、`/alarm`、`/audit`、`/metric`、`/api`
转发到平台端 `http://localhost:8100`，因此本地启动平台端后无需额外配置跨域。

生产环境默认与平台端同源部署（由 Nginx 将上述前缀反向代理到平台端）；
如前后端分域部署，构建前设置环境变量指定平台端地址：

```bash
# .env.production
VUE_APP_BASE_API=http://platform-host:8100
```

> 构建入口为 `public/index.html`。

## 目录

```
src/
  api/
    index.js        统一出口（页面统一 import * as api from '@/api'）
    api.js          平台端接口封装，按 Controller 分组
  utils/format.js   时间/数值/状态格式化
  components/       Sidebar、Topbar
  views/            Dashboard、RealtimeMonitor、HistoryMonitor、AlarmRules、AlarmRecords
  router/index.js   路由
```

## 页面与平台端接口映射

| 页面 | 接口 | 平台端来源 |
| --- | --- | --- |
| 监控仪表 | `GET /monitor/redis/instances` | MonitorController#instances |
| 监控仪表 | `GET /alarm/rule/overview` | AlarmController#ruleOverview |
| 监控仪表 | `GET /alarm/rule/recent` | AlarmController#recent |
| 监控仪表 | `GET /monitor/realtime/current-values` | MonitorController#currentValues |
| 监控仪表 | `GET /monitor/realtime/trend` | MonitorController#trend |
| 监控仪表 | `GET /monitor/sentinel/topology` | MonitorController#topology |
| 监控仪表（操作日志） | `GET /audit/logs?pageNum=1&pageSize=5` | AuditController#logs |
| 实时监控 | `GET /monitor/redis/instances` | MonitorController#instances |
| 实时监控 | `GET /monitor/realtime/current-values` | MonitorController#currentValues |
| 实时监控 | `GET /monitor/realtime/trend` | MonitorController#trend |
| 实时监控 | `GET /monitor/sentinel/sentinels` | MonitorController#sentinels |
| 实时监控 | `GET /monitor/sentinel/nodes-status` | MonitorController#nodesStatus |
| 实时监控 | `GET /monitor/sentinel/topology` | MonitorController#topology |
| 历史监控 | `GET /monitor/realtime/trend` | MonitorController#trend |
| 历史监控 | `POST /monitor/report/query` | MonitorController#reportQuery |
| 历史监控 | `GET /monitor/report/export` | MonitorController#reportExport |
| 历史监控 | `GET /monitor/report/export/status`（3s 轮询） | MonitorController#exportStatus |
| 历史监控 | `GET /monitor/report/export/download` | MonitorController#exportDownload |
| 告警监控 | `GET /alarm/rule/overview` | AlarmController#ruleOverview |
| 告警监控 | `GET /alarm/rule/recent-unrecovered` | AlarmController#recentUnrecovered |
| 告警监控 | `GET /alarm/rule/list` | AlarmController#getAlarmRuleList |
| 告警监控 | `POST /alarm/rule/add`、`/update`、`/delete`、`/toggle-status` | AlarmController |
| 告警监控 | `POST /alarm/rule/trigger-check`、`/trigger-all-check` | AlarmController |
| 告警记录 | `GET /alarm/record/overview` | AlarmController#recordOverview |
| 告警记录 | `GET /alarm/record/page` | AlarmController#recordPage |
| 告警记录 | `GET /alarm/record/detail` | AlarmController#recordDetail |
| 告警记录 | `GET /alarm/record/metric-trend` | AlarmController#metricTrend |
| 告警记录 | `POST /alarm/record/recover` | AlarmController#recoverRecord |

## 与平台端的字段约定（联调重点）

- **统一响应**：`{ code, data, message }`，`code === 0` 为成功；业务异常同样返回 HTTP 200，因此
  `src/api/api.js` 在响应拦截器中按 `code` 解包并抛出 `message`。
- **分页响应**：规则/告警/审计分页统一为 `{ page: { records, total, current, size } }`，
  页面读取 `data.page.records` / `data.page.total`，**不是** `data.list`。
- **趋势响应**：`GET /monitor/realtime/trend` 返回
  `{ timestampList: string[], metricList: [{ metricKey, metricName, unit, seriesList: [{ instanceId, points: [{bucket,value}] }] }] }`。
  页面先按 `metricKey` 选中指标，再取 `seriesList`；没有顶层 `seriesList`。
- **时间**：平台端 `LocalDateTime` 返回 ISO 格式；页面统一用 `utils/format.js` 的 `formatDateTime`
  转为 `yyyy-MM-dd HH:mm:ss`；查询参数（`startTime`/`endTime`）必须是该格式。
- **状态值**：`runStatus`/`sentinelStatus`/`metricStatus`/`copyStatus` 返回中文描述
  （正常/警告/严重/离线/在线/延迟/无数据）；`alarmLevel`、`recoverStatus`、`enableStatus`、
  `scopeType` 是数字字符串，页面在 `normalize*` 中统一转 `Number` 后再比较。
- **告警级别**：`1=提示`、`2=警告`、`3=严重`。
- **告警记录 id**：详情/指标曲线接口入参是告警记录主键 `alarmRecordId`，不是展示用的 `alarmNo`。
- **告警规则 `duration`**：平台端单位是「采集周期个数」（默认 1），不是秒；
  `silenceDuration` 单位是秒（默认 1800），`collectInterval` 单位是秒（默认 300）。
- **告警规则新增/修改**：
  - `enableStatus` 必须是字符串 `"0"/"1"`，传 boolean 会被 Jackson 转成 `"true"/"false"` 导致枚举校验失败；
  - `unit` 允许空串但不能省略；
  - 修改接口要求 `metricName` 为后端枚举中文名（`CommonConstant.METRIC_NAMES`），页面通过 `metricNameMap` 映射；
  - `DELETE /alarm/rule/delete`、`trigger-check` 是 `@RequestParam`，必须放 query，不能放 body。
- **纳管状态**：实例/哨兵列表只查 `managementStatus=1`。执行器注册后默认未纳管，
  需平台侧调用 `POST /monitor/executor/management-status` 纳管；前端“执行器列表/手工纳管”按钮暂未开发，
  因此新执行器在纳管前不会出现在本前端默认列表中，但采集数据仍会落库。
- **告警记录概览**：平台端返回扁平字段 `infoCount/alarmCount/severityCount`，页面组装为
  `alarmLevelCount.{info,alarm,severity}`；`startTime/endTime` 可选，缺省最近 3 天。
- **节点状态**：`copyDelay` 单位是**秒**，`pingLatency` 是执行器 ping 平台的 **毫秒**延迟；
  实时监控主从表新增「连通延迟」列展示 `pingLatency`。
- **报告概览数值**：平台端 `ReportOverviewDTO` 的均值/峰值是字符串，页面先 `toNumber` 再 `toFixed`。

## 本次联调修复清单

1. 修正趋势图数据解析：`TrendResponse.metricList` → `seriesList`（Dashboard / RealtimeMonitor / HistoryMonitor）。
2. 修正三类分页响应解析：`data.page.records` / `data.page.total`（规则、告警记录、操作审计）。
3. 修正规则删除、单规则手动检测的 `ruleId` 传参（query 而非 body）。
4. 修正规则保存：`enableStatus` 统一字符串、`unit` 允许空串、修改时补 `metricName`、指标枚举与后端对齐。
5. 修正规则启用/禁用切换参数，避免 boolean 被后端枚举校验拒绝。
6. 历史监控概览数值字符串安全转换，避免 `.toFixed is not a function`。
7. 实时监控主从表复制延迟单位改为秒，新增「连通延迟」列展示执行器 ping 平台延迟。
8. 构建配置关闭 Terser 多进程，兼容受限构建环境。
9. 补充操作审计、告警人工恢复、导出下载地址等接口封装，并统一错误提示。
10. 顶栏「立即刷新 / 每 5min 自动刷新」统一调用各页面 `loadData()`，页面销毁时释放 ECharts 与定时器。

## 平台端能力（前端暂未提供入口）

- `POST /alarm/record/recover`：告警人工确认恢复，已在 `api.js` 暴露 `recoverRecord`，
  如需在告警详情弹窗中增加「确认恢复」操作可直接调用。
- `GET /audit/logs`：操作审计分页查询，当前仅在仪表盘展示最近 5 条。
