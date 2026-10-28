import axios from 'axios'

/**
 * 平台端接口统一封装，与 redis-monitor-platform 的 Controller 对齐：
 *  - MonitorController   /monitor/**
 *  - AlarmController     /alarm/**
 *  - AuditController     /audit/**
 *  - ExecutorController  /agent/**  （执行器注册/心跳，前端不使用）
 *
 * 平台端统一响应结构：{ code, data, message }，code === 0 表示成功，
 * 业务异常同样以 HTTP 200 返回，需要按 code 判断。
 */
const BASE_API = process.env.VUE_APP_BASE_API || ''

const request = axios.create({
  baseURL: BASE_API,
  timeout: 20000,
  // Spring 对 List 参数按同名 key 重复接收（instanceIds=a&instanceIds=b），
  // 因此这里不能使用 axios 默认的 instanceIds[]=a 形式。
  paramsSerializer: params => {
    const parts = []
    Object.keys(params || {}).forEach(key => {
      const value = params[key]
      if (value === undefined || value === null || value === '') return
      if (Array.isArray(value)) {
        value.forEach(item => {
          if (item === undefined || item === null || item === '') return
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(item)}`)
        })
        return
      }
      parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    })
    return parts.join('&')
  }
})

/**
 * 页面级取消令牌：路由切换时取消上一页未完成的请求。
 * 请求拦截器会为没有显式 cancelToken 的请求自动挂上当前页面令牌。
 */
let pageCancelSource = axios.CancelToken.source()

request.interceptors.request.use(config => {
  if (!config.cancelToken) {
    config.cancelToken = pageCancelSource.token
  }
  return config
})

/** 路由切换时调用：取消当前页面所有未完成请求，并为新页面创建新令牌 */
export const resetRequestCancelToken = () => {
  pageCancelSource.cancel('页面切换，已取消未完成请求')
  pageCancelSource = axios.CancelToken.source()
}

request.interceptors.response.use(
  res => {
    const body = res.data
    // 普通 JSON 响应：按平台端统一结构解包
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
      if (body.code === 0) return body.data
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 文件流等非统一结构响应原样返回
    return body
  },
  err => {
    // 页面切换触发的取消不需要弹错误；返回 pending 避免已销毁页面写入状态
    if (axios.isCancel(err)) {
      return new Promise(() => {})
    }
    if (err.response) {
      const data = err.response.data
      if (data && data.message) return Promise.reject(new Error(data.message))
      return Promise.reject(new Error(`请求失败（HTTP ${err.response.status}）`))
    }
    if (err.code === 'ECONNABORTED' || /timeout/i.test(err.message || '')) {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }
    return Promise.reject(new Error(err.message || '网络异常，无法连接平台端服务'))
  }
)

/* ==================== 监控相关：MonitorController ==================== */

/** 已纳管 Redis 实例列表，managementStatus：1=已纳管 / 0=未纳管 */
export const getInstances = (managementStatus = 1) =>
  request.get('/monitor/redis/instances', { params: { managementStatus } })

/** 单实例核心实时指标（7 项） */
export const getCurrentValues = instanceId =>
  request.get('/monitor/realtime/current-values', { params: { instanceId } })

/** 多节点指标趋势，timeRange：近1h / 近6h；返回 { timestampList, metricList:[{metricKey,metricName,unit,seriesList}] } */
export const getTrend = (instanceIds, metricKey, timeRange) =>
  request.get('/monitor/realtime/trend', { params: { instanceIds, metricKey, timeRange } })

/** Sentinel 节点列表 */
export const getSentinels = (managementStatus = 1) =>
  request.get('/monitor/sentinel/sentinels', { params: { managementStatus } })

/** 主从拓扑（含最近一次切换事件） */
export const getTopology = () => request.get('/monitor/sentinel/topology')

/** 主从状态表格 */
export const getNodesStatus = () => request.get('/monitor/sentinel/nodes-status')

/* ==================== 日/周报：MonitorController#report ==================== */

/** 日周报查询，reportType：日报 / 周报；返回 { overview, trendList, eventList } */
export const queryReport = (instanceId, reportType) =>
  request.post('/monitor/report/query', { instanceId, reportType })

/** 提交异步导出任务，返回 { taskId, taskStatus } */
export const exportReport = (instanceId, reportType) =>
  request.get('/monitor/report/export', { params: { instanceId, reportType } })

/** 查询导出任务状态，taskStatus：pending / success / fail */
export const getExportStatus = taskId =>
  request.get('/monitor/report/export/status', { params: { taskId } })

/** 导出文件下载地址（任务成功后由浏览器直接下载，30 分钟有效） */
export const getExportDownloadUrl = taskId =>
  BASE_API + '/monitor/report/export/download?taskId=' + encodeURIComponent(taskId)

/* ==================== 告警规则：AlarmController#rule ==================== */

/** 规则分页查询，params：keyword/alarmLevel/enableStatus/scopeInstanceIds/pageNum/pageSize
 *  返回 { scopeInstanceNames, page:{ records, total, current, size } } */
export const getRuleList = params => request.get('/alarm/rule/list', { params })
export const addRule = data => request.post('/alarm/rule/add', data)
export const updateRule = data => request.post('/alarm/rule/update', data)
/** 平台端以 @RequestParam 接收 ruleId，必须放 query 而不是 body */
export const deleteRule = ruleId => request.post('/alarm/rule/delete', null, { params: { ruleId } })
export const toggleRuleStatus = (ruleId, enableStatus) =>
  request.post('/alarm/rule/toggle-status', { ruleId, enableStatus })
/** 平台端以 @RequestParam 接收 ruleId，必须放 query 而不是 body */
export const triggerCheck = ruleId => request.post('/alarm/rule/trigger-check', null, { params: { ruleId } })
export const triggerAllCheck = () => request.post('/alarm/rule/trigger-all-check')

/** 告警监控页概览：已启用规则数/未恢复告警/今日告警分级/检测任务状态 */
export const getRuleOverview = () => request.get('/alarm/rule/overview')
/** 未恢复告警列表（最近 5 条） */
export const getRecentUnrecovered = () => request.get('/alarm/rule/recent-unrecovered')
/** 最近告警列表（最近 5 条） */
export const getRecentAlarms = () => request.get('/alarm/rule/recent')

/* ==================== 告警记录：AlarmController#record ==================== */

/** 记录概览，startTime/endTime 格式 yyyy-MM-dd HH:mm:ss，缺省为今日 */
export const getRecordOverview = (startTime, endTime) =>
  request.get('/alarm/record/overview', { params: { startTime, endTime } })

/** 记录分页查询，返回 { page:{ records, total, current, size } } */
export const getRecordPage = params => request.get('/alarm/record/page', { params })

/** 记录详情，入参为告警记录 id（不是 alarmNo） */
export const getRecordDetail = alarmRecordId =>
  request.get('/alarm/record/detail', { params: { alarmRecordId } })

/** 告警关联指标波动曲线，入参为告警记录 id */
export const getMetricTrend = alarmRecordId =>
  request.get('/alarm/record/metric-trend', { params: { alarmRecordId } })

/** 人工确认恢复告警（平台端以 @RequestParam 接收） */
export const recoverRecord = alarmRecordId =>
  request.post('/alarm/record/recover', null, { params: { alarmRecordId } })

/* ==================== 操作审计：AuditController ==================== */

/** 操作审计分页查询，仪表盘取最近 5 条时传 pageNum=1&pageSize=5；
 *  返回 { page:{ records, total, current, size } } */
export const getAuditLogs = params => request.get('/audit/logs', { params })
