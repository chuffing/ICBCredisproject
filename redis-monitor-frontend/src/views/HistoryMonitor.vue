<template>
  <div>
    <!-- 运行报告查询 -->
    <div class="panel">
      <div class="panel-title">运行报告查询</div>
      <div class="panel-desc">查询当天或近7天Redis运行数据并生成运行报告</div>
      <div class="query-row">
        <div class="form-item">
          <label class="form-label">Redis实例</label>
          <el-select v-model="query.instanceId" placeholder="请选择Redis实例" class="instance-select" @change="onInstanceChange">
            <el-option v-for="i in instances" :key="i.instanceId"
              :label="i.instanceName + '｜' + i.hostIp + ':' + i.redisPort" :value="i.instanceId" />
          </el-select>
        </div>
        <div class="form-item">
          <label class="form-label">报告类型</label>
          <div class="report-type-group">
            <button class="report-type" :class="{ active: reportType === '日报' }" @click="changeReportType('日报')">当天</button>
            <button class="report-type" :class="{ active: reportType === '周报' }" @click="changeReportType('周报')">近7天</button>
          </div>
        </div>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </div>

    <!-- 运行报告概览 -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">运行报告</div>
          <div class="panel-desc report-range">{{ reportRangeText }}</div>
        </div>
        <el-button size="small" plain :loading="exporting" @click="exportReport">导出报告</el-button>
      </div>

      <div class="summary-grid report-summary">
        <div class="summary-card">
          <div class="summary-title">CPU平均使用率</div>
          <div class="summary-value">{{ percent(overview.cpuAvg) }}<span class="summary-unit">%</span></div>
          <div class="summary-desc">峰值{{ percent(overview.cpuMax) }}%<template v-if="peakTime(overview.cpuMaxTim)">｜{{ peakTime(overview.cpuMaxTim) }}</template></div>
        </div>
        <div class="summary-card">
          <div class="summary-title">Redis内存平均使用量</div>
          <div class="summary-value">{{ decimal(overview.redisMemAvg) }}<span class="summary-unit">GB</span></div>
          <div class="summary-desc">峰值{{ decimal(overview.redisMemMax) }}GB<template v-if="peakTime(overview.redisMemMaxTim)">｜{{ peakTime(overview.redisMemMaxTim) }}</template></div>
        </div>
        <div class="summary-card">
          <div class="summary-title">QPS平均值</div>
          <div class="summary-value">{{ integer(overview.qpsAvg) }}</div>
          <div class="summary-desc">峰值{{ integer(overview.qpsMax) }}<template v-if="peakTime(overview.qpsMaxTim)">｜{{ peakTime(overview.qpsMaxTim) }}</template></div>
        </div>
        <div class="summary-card">
          <div class="summary-title">平均延迟</div>
          <div class="summary-value">{{ decimal(overview.latencyAvg) }}<span class="summary-unit">ms</span></div>
          <div class="summary-desc">峰值{{ decimal(overview.latencyMax) }}ms<template v-if="peakTime(overview.latencyMaxTim)">｜{{ peakTime(overview.latencyMaxTim) }}</template></div>
        </div>
        <div class="summary-card">
          <div class="summary-title">告警次数</div>
          <div class="summary-value orange">{{ alarmStats.alarmTotal || 0 }}</div>
          <div class="summary-desc">严重{{ alarmStats.severityCount || 0 }}｜警告{{ alarmStats.alarmCount || 0 }}｜提示{{ alarmStats.infoCount || 0 }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-title">主从切换次数</div>
          <div class="summary-value">{{ overview.switchTotal || 0 }}</div>
          <div class="summary-desc">最近切换：{{ peakTime(overview.lastSwitchTime) || '--' }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-title">Redis服务可用率</div>
          <div class="summary-value green">{{ decimal(overview.serviceAvailableRate) }}<span class="summary-unit">%</span></div>
          <div class="summary-desc">整体运行稳定</div>
        </div>
        <div class="summary-card">
          <div class="summary-title">当前节点数</div>
          <div class="summary-value">{{ nodeTotal }}</div>
          <div class="summary-desc">MASTER{{ overview.masterCount || 0 }}｜REPLICA{{ overview.replicaCount || 0 }}</div>
        </div>
      </div>
    </div>

    <!-- 监控指标统计 -->
    <div class="panel">
      <div class="panel-title">监控指标统计</div>
      <div class="panel-desc">展示报告周期内主要Redis监控指标变化</div>
      <div class="chart-grid">
        <div v-for="def in chartDefs" :key="def.key" class="chart-card">
          <div class="chart-title">{{ def.title }}</div>
          <div class="chart-desc">{{ def.desc }}</div>
          <div :ref="'historyChart-' + def.key" class="chart-box"></div>
        </div>
      </div>
    </div>

    <!-- 告警与主从切换统计 -->
    <div class="panel">
      <div class="panel-title">告警与主从切换统计</div>
      <div class="panel-desc">展示报告周期内告警及主从切换情况</div>
      <table class="data-table" style="margin-top:6px">
        <thead><tr><th>类型</th><th>Redis节点</th><th>内容</th><th>发生时间</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="(e, i) in events" :key="i">
            <td><span class="tag" :class="e.eventType==='告警'?'yellow':'blue'">{{ e.eventType }}</span></td>
            <td>{{ e.nodeDisplay }}</td>
            <td>{{ e.content }}</td>
            <td>{{ e.happenTime }}</td>
            <td><span class="tag" :class="eventTagClass(e.status)">{{ e.status }}</span></td>
          </tr>
          <tr v-if="!events.length"><td colspan="5" style="text-align:center;color:#909399">暂无事件</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import * as api from '@/api'
import * as echarts from 'echarts'
import { formatDateTime, formatTime, toNumber } from '@/utils/format'

/** 报告周期内 4 项指标：每项独立成图、独立纵轴 */
const CHART_DEFS = [
  { key: 'cpuUsage', title: 'CPU使用率', desc: '平均值及峰值变化', unit: '%', color: '#409eff' },
  { key: 'redisMemUsed', title: 'Redis内存使用量', desc: 'Redis内存使用变化', unit: 'GB', color: '#20a86a' },
  { key: 'qps', title: 'QPS', desc: 'Redis吞吐量变化', unit: '次/秒', color: '#7053d8' },
  { key: 'avgLatency', title: '请求延迟', desc: 'Redis平均响应延迟', unit: 'ms', color: '#e39b34' }
]

const HISTORY_INSTANCE_KEY = 'redis-monitor:history:instanceId'

export default {
  name: 'HistoryMonitor',
  data() {
    return {
      instances: [],
      query: { instanceId: '' },
      reportType: '日报',
      overview: {},
      trends: [],
      events: [],
      loading: false,
      initialLoading: false,
      reportSeq: 0,
      exporting: false,
      exportTimer: null,
      chartDefs: CHART_DEFS
    }
  },
  computed: {
    alarmStats() { return this.overview.alarmStatsDTO || {} },
    nodeTotal() { return Number(this.overview.masterCount || 0) + Number(this.overview.replicaCount || 0) },
    reportRangeText() {
      const now = new Date()
      const fmt = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      if (this.reportType === '周报') {
        const start = new Date(now)
        start.setDate(now.getDate() - 6)
        return `统计范围：${fmt(start)} 至 ${fmt(now)}`
      }
      return `统计范围：${fmt(now)}`
    }
  },
  mounted() {
    this.loadData()
    window.addEventListener('resize', this.resizeCharts)
  },
  created() {
    // ECharts 实例不要放进 data，避免被 Vue 2 深度观察导致图表异常
    this.charts = {}
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    this.reportSeq += 1
    if (this.exportTimer) { clearInterval(this.exportTimer); this.exportTimer = null }
    Object.keys(this.charts).forEach(key => {
      if (this.charts[key]) this.charts[key].dispose()
    })
    this.charts = {}
  },
  methods: {
    // 页面数据加载入口
    async loadData() {
      if (this.initialLoading) return
      this.initialLoading = true
      try {
        // 复用上一次选中的实例，先发报告请求；实例列表返回后再校正
        const initialId = this.query.instanceId || localStorage.getItem(HISTORY_INSTANCE_KEY) || ''
        this.query.instanceId = initialId
        const reportPromise = initialId ? this.loadReport(initialId) : Promise.resolve()
        const changed = await this.loadInstances()
        await reportPromise
        if (this.query.instanceId && (changed || this.query.instanceId !== initialId)) {
          await this.loadReport(this.query.instanceId)
        }
      } finally {
        this.initialLoading = false
      }
    },
    async loadInstances() {
      const previousId = this.query.instanceId
      try {
        this.instances = (await api.getInstances()) || []
        // 默认展示当前 Master 节点
        const exists = this.instances.some(i => i.instanceId === this.query.instanceId)
        if (!exists) {
          const master = this.instances.find(i => String(i.role || '').toUpperCase() === 'MASTER')
          this.query.instanceId = master ? master.instanceId : (this.instances[0] ? this.instances[0].instanceId : '')
        }
        this.saveInstanceId()
        return this.query.instanceId !== previousId
      } catch (e) {
        this.$message.error(e.message || '实例列表加载失败')
        return false
      }
    },
    saveInstanceId() {
      if (this.query.instanceId) {
        localStorage.setItem(HISTORY_INSTANCE_KEY, this.query.instanceId)
      }
    },
    onInstanceChange() {
      this.saveInstanceId()
      if (this.query.instanceId) this.loadReport(this.query.instanceId)
    },
    changeReportType(type) {
      if (this.reportType === type) return
      this.reportType = type
      if (this.query.instanceId) this.loadReport(this.query.instanceId)
    },
    search() {
      if (!this.query.instanceId) { this.$message.warning('请先选择Redis实例'); return }
      this.saveInstanceId()
      this.loadReport(this.query.instanceId)
    },
    resetQuery() {
      const master = this.instances.find(i => String(i.role || '').toUpperCase() === 'MASTER')
      this.query.instanceId = master ? master.instanceId : (this.instances[0] ? this.instances[0].instanceId : '')
      this.reportType = '日报'
      this.overview = {}
      this.trends = []
      this.events = []
      this.saveInstanceId()
      if (this.query.instanceId) this.loadReport(this.query.instanceId)
      else this.renderCharts()
    },
    resizeCharts() {
      Object.keys(this.charts).forEach(key => {
        if (this.charts[key]) this.charts[key].resize()
      })
    },
    async loadReport(instanceId) {
      const targetId = instanceId || this.query.instanceId
      if (!targetId) return
      const seq = ++this.reportSeq
      this.loading = true
      try {
        const data = await api.queryReport(targetId, this.reportType)
        if (seq !== this.reportSeq) return
        this.overview = (data && data.overview) || {}
        this.trends = (data && data.trendList) || []
        this.events = ((data && data.eventList) || []).map(item => ({
          ...item,
          happenTime: formatDateTime(item.happenTime)
        }))
      } catch (e) {
        if (seq !== this.reportSeq) return
        this.overview = {}
        this.trends = []
        this.events = []
        this.$message.error(e.message || '报告数据加载失败')
      } finally {
        if (seq === this.reportSeq) this.loading = false
      }
      if (seq !== this.reportSeq) return
      await this.$nextTick()
      this.renderCharts()
      await this.$nextTick()
      this.resizeCharts()
    },
    renderCharts() {
      this.chartDefs.forEach(def => {
        // v-for 内的 ref 在 Vue 2 中统一注册为数组
        const raw = this.$refs['historyChart-' + def.key]
        const el = Array.isArray(raw) ? raw[0] : raw
        if (!el) return
        if (!this.charts[def.key]) this.charts[def.key] = echarts.init(el)
        const chart = this.charts[def.key]
        const trend = this.trends.find(t => t.metricKey === def.key) || {}
        const timestamps = trend.timestampList || []
        const values = trend.valueList || []
        const unit = trend.unit || def.unit
        const labels = timestamps.map(t => {
          const text = String(t)
          return this.reportType === '周报' ? text.slice(5, 16) : text.slice(11, 16)
        })
        const labelInterval = Math.max(0, Math.ceil(labels.length / 5) - 1)
        chart.setOption({
          animation: false,
          tooltip: {
            trigger: 'axis',
            valueFormatter: value => (value === null || value === undefined ? '--' : `${value}${unit}`)
          },
          grid: { left: 5, right: 12, top: 18, bottom: 6, containLabel: true },
          xAxis: {
            type: 'category',
            boundaryGap: false,
            data: labels,
            axisTick: { show: false },
            axisLine: { lineStyle: { color: '#e9edf4' } },
            axisLabel: { fontSize: 10, color: '#909399', interval: labelInterval, hideOverlap: true }
          },
          yAxis: {
            type: 'value',
            min: 0,
            name: unit,
            nameTextStyle: { fontSize: 10, color: '#909399', align: 'right' },
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { fontSize: 10, color: '#909399' },
            splitLine: { lineStyle: { color: '#f0f3f8' } }
          },
          series: [{
            name: def.title,
            type: 'line',
            smooth: true,
            showSymbol: false,
            connectNulls: false,
            data: values,
            lineStyle: { width: 2, color: def.color },
            itemStyle: { color: def.color },
            areaStyle: { color: def.color, opacity: 0.12 }
          }]
        }, true)
      })
    },
    async exportReport() {
      if (!this.query.instanceId) { this.$message.warning('请先选择Redis实例'); return }
      if (this.exporting) { this.$message.warning('导出任务正在生成中，请稍候'); return }
      this.exporting = true
      try {
        const data = await api.exportReport(this.query.instanceId, this.reportType)
        if (!data || !data.taskId) {
          this.exporting = false
          this.$message.error('导出任务提交失败')
          return
        }
        this.$message.success('导出任务已提交，正在生成...')
        this.pollExport(data.taskId)
      } catch (e) {
        this.exporting = false
        this.$message.error(e.message || '导出失败')
      }
    },
    pollExport(taskId) {
      if (this.exportTimer) { clearInterval(this.exportTimer); this.exportTimer = null }
      let attempts = 0
      this.exportTimer = setInterval(async () => {
        attempts += 1
        try {
          const data = await api.getExportStatus(taskId)
          const status = data && data.taskStatus
          if (status === 'success') {
            clearInterval(this.exportTimer)
            this.exportTimer = null
            this.exporting = false
            this.downloadExport(taskId)
          } else if (status === 'fail') {
            clearInterval(this.exportTimer)
            this.exportTimer = null
            this.exporting = false
            this.$message.error((data && data.failReason) || '导出失败')
          } else if (attempts >= 100) {
            clearInterval(this.exportTimer)
            this.exportTimer = null
            this.exporting = false
            this.$message.warning('导出任务超时')
          }
        } catch (e) {
          clearInterval(this.exportTimer)
          this.exportTimer = null
          this.exporting = false
          this.$message.error(e.message || '导出状态查询失败')
        }
      }, 3000)
    },
    downloadExport(taskId) {
      const link = document.createElement('a')
      link.href = api.getExportDownloadUrl(taskId)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    },
    percent(value) {
      const num = toNumber(value)
      return num === null ? '--' : num.toFixed(1)
    },
    decimal(value) {
      const num = toNumber(value)
      return num === null ? '--' : num.toFixed(2)
    },
    integer(value) {
      const num = toNumber(value)
      return num === null ? '--' : Math.round(num).toLocaleString()
    },
    peakTime(value) {
      if (!value) return ''
      const time = formatTime(value, '')
      return time ? time.slice(0, 5) : ''
    },
    eventTagClass(status) {
      if (status === '未恢复' || status === '失败') return 'red'
      if (status === '已恢复' || status === '成功' || status === '恢复') return 'green'
      return 'yellow'
    }
  }
}
</script>

<style scoped>
.query-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.form-item { min-width: 180px; }
.form-label {
  display: block;
  font-size: 13px;
  color: #606266;
  margin-bottom: 7px;
}
.instance-select { width: 270px; }
.report-type-group { display: flex; gap: 8px; }
.report-type {
  height: 40px;
  padding: 0 18px;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: .2s;
}
.report-type:hover { border-color: #409eff; color: #409eff; }
.report-type.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}
.report-summary { margin-bottom: 0; }
.summary-unit { font-size: 12px; color: #909399; margin-left: 3px; font-weight: normal; }
.report-range { margin-bottom: 0; }
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-top: 18px;
}
.chart-card {
  border: 1px solid #ebeef5;
  border-radius: 7px;
  padding: 16px;
  min-width: 0;
}
.chart-title { font-size: 14px; font-weight: bold; margin-bottom: 6px; }
.chart-desc { font-size: 12px; color: #909399; margin-bottom: 10px; }
.chart-box { height: 250px; width: 100%; }
@media (max-width: 1100px) {
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
