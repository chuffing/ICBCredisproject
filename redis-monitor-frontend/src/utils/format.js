/**
 * 展示层格式化工具。
 * 平台端 LocalDateTime 默认按 ISO 格式（2026-10-20T14:30:00）返回，
 * 页面统一通过这里转换为 `yyyy-MM-dd HH:mm:ss` 展示。
 */

function pad2(value) {
  return String(value).padStart(2, '0')
}

/**
 * 格式化为 yyyy-MM-dd HH:mm:ss。
 * 兼容 ISO 字符串（带 T / 毫秒 / 时区）、时间戳与 Date 对象。
 */
export function formatDateTime(value, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback
  if (typeof value === 'string') {
    // 带时区偏移的字符串交给 Date 统一换算，本地时间字符串直接拼装
    const hasZone = /[zZ]$|[+-]\d{2}:?\d{2}$/.test(value.trim())
    const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/)
    if (matched && !hasZone) {
      return `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}:${matched[6] || '00'}`
    }
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return fallback
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ` +
    `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

/** 只取时分秒，用于「操作时间」等紧凑列 */
export function formatTime(value, fallback = '--') {
  const full = formatDateTime(value, '')
  return full ? full.slice(11) : fallback
}

/** 数值千分位展示，空值展示占位符 */
export function formatNumber(value, fallback = '--') {
  if (value === null || value === undefined || value === '') return fallback
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return num.toLocaleString()
}

/** 安全转数字，无法转换时返回 fallback */
export function toNumber(value, fallback = null) {
  if (value === null || value === undefined || value === '') return fallback
  const num = Number(value)
  return Number.isNaN(num) ? fallback : num
}

/** 告警级别 1/2/3 → 提示/警告/严重（兼容后端返回的字符串数字） */
export function alarmLevelText(level) {
  const num = toNumber(level)
  if (num === 3) return '严重'
  if (num === 2) return '警告'
  if (num === 1) return '提示'
  return '--'
}

/** 告警级别对应的现有 tag 颜色类 */
export function alarmLevelClass(level) {
  const num = toNumber(level)
  if (num === 3) return 'red'
  if (num === 2) return 'yellow'
  return 'blue'
}

/** 指标状态（正常/提示/警告/严重/无数据）→ metric-status 颜色类 */
export function metricStatusClass(status) {
  if (status === '正常') return ''
  if (status === '严重') return 'error'
  return 'warn'
}

/** 拓扑状态（健康/警告/异常）→ tag 颜色类 */
export function topologyStatusClass(status) {
  if (status === '健康') return 'green'
  if (status === '警告') return 'yellow'
  if (status === '异常') return 'red'
  return 'blue'
}
