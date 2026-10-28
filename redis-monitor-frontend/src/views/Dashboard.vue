<template>
  <div>
    <!-- 统计卡片 -->
    <section class="stat-grid">
      <div class="card stat-card">
        <div class="stat-icon blue">▦</div>
        <div>
          <div class="stat-label">Redis实例数</div>
          <div class="stat-value">{{ instances.length }}</div>
          <span class="stat-tiny">运行中 <i class="ok">{{ runningCount }}</i>　异常 <i class="status-no">{{ abnormalCount }}</i></span>
        </div>
      </div>
      <div class="card stat-card">
        <div class="stat-icon purple">〽</div>
        <div>
          <div class="stat-label">QPS</div>
          <div class="stat-value">{{ formatNum(metricData.qps) }}</div>
          <span class="stat-tiny">较上一采集周期 <i :class="metricData.qpsChange>=0?'up':'down'">{{ metricData.qpsChange>=0?'↑':'↓' }} {{ Math.abs(metricData.qpsChange||0).toFixed(1) }}%</i></span>
        </div>
      </div>
      <div class="card stat-card">
        <div class="stat-icon green">◷</div>
        <div>
          <div class="stat-label">平均延迟</div>
          <div class="stat-value">{{ metricData.latency || '--' }}<small style="font-size:14px"> ms</small></div>
          <span class="stat-tiny">较上一采集周期 <i :class="metricData.latencyChange<=0?'down':'up'">{{ metricData.latencyChange<=0?'↓':'↑' }} {{ Math.abs(metricData.latencyChange||0).toFixed(1) }}%</i></span>
        </div>
      </div>
      <div class="card stat-card">
        <div class="stat-icon orange">▣</div>
        <div>
          <div class="stat-label">内存使用率</div>
          <div class="stat-value">{{ metricData.mem || '--' }}<small style="font-size:14px"> %</small></div>
          <span class="stat-tiny">较上一采集周期 <i class="up">↑ {{ metricData.memChange||0 }}%</i></span>
        </div>
      </div>
      <div class="card stat-card">
        <div class="stat-icon red">♟</div>
        <div>
          <div class="stat-label">今日告警</div>
          <div class="stat-value">{{ overview.todayAlarmTotal || 0 }}</div>
          <span class="stat-tiny">未处理 <i class="status-no">{{ overview.unrecoveredAlarmCount || 0 }}</i></span>
        </div>
      </div>
    </section>

    <!-- 拓扑 + 实时图表 -->
    <section style="display:grid;grid-template-columns:360px 1fr;gap:14px;margin-top:14px">
      <div class="card">
        <div class="panel-title">哨兵监控·主从拓扑
          <span class="right" style="background:#eaf8ef;color:#20a867;padding:5px 10px;border-radius:4px;font-size:12px;font-weight:500">{{ topologyStatus }}</span>
        </div>
        <div class="topology-box">
          <div v-for="(n, i) in topologyNodes" :key="i" class="topo-node" :class="n.role" :style="n.style">
            <strong>{{ n.name }}</strong>
            <span>{{ n.addr }}</span>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="panel-title">实时监控 <span class="sub">主节点数据 · 近1小时</span></div>
        <!-- 4 个指标各一张趋势图，纵轴按各自指标单位独立展示 -->
        <div class="realtime-charts">
          <div v-for="def in chartDefs" :key="def.key" class="rt-chart">
            <h4>{{ def.title }}</h4>
            <div :ref="'rtChart-' + def.key" class="rt-chart-box"></div>
            <div class="rt-legend">
              <span class="dot" :style="{ background: def.color }"></span>
              <span class="rt-legend-text">{{ legendText(def) }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 最近告警 + 操作日志 -->
    <section style="display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px">
      <div class="card">
        <div class="panel-title">最近告警 <span class="sub">最近5条</span>
          <span class="right" style="color:#2784ed;font-size:12px;cursor:pointer" @click="$router.push('/alarm-records')">查看全部</span>
        </div>
        <div style="padding:0 12px 14px">
          <table class="data-table">
            <thead><tr><th>告警级别</th><th>实例</th><th>告警内容</th><th>触发时间</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="a in recentAlarms" :key="a.alarmRecordId">
                <td><span class="tag" :class="a.alarmLevel===3?'red':a.alarmLevel===2?'yellow':'blue'">{{ levelText(a.alarmLevel) }}</span></td>
                <td>{{ a.instanceDisplay }}</td>
                <td>{{ a.alarmContent }}</td>
                <td>{{ a.triggerTime }}</td>
                <td :class="a.alarmStatus===0?'status-no':'status-ok'">{{ a.alarmStatus===0?'未处理':'已处理' }}</td>
              </tr>
              <tr v-if="!recentAlarms.length"><td colspan="5" style="text-align:center;color:#909399">暂无数据</td></tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="card">
        <div class="panel-title">操作日志 <span class="sub">最近5条</span></div>
        <div style="padding:0 12px 14px">
          <table class="data-table">
            <thead><tr><th>操作类型</th><th>操作人</th><th>操作内容</th><th>操作时间</th></tr></thead>
            <tbody>
              <tr v-for="(log, i) in auditLogs" :key="i">
                <td><span class="tag" :class="log.typeClass">{{ log.type }}</span></td>
                <td>{{ log.user }}</td>
                <td>{{ log.content }}</td>
                <td>{{ log.time }}</td>
              </tr>
              <tr v-if="!auditLogs.length"><td colspan="4" style="text-align:center;color:#909399">暂无数据</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import * as api from '@/api'
import * as echarts from 'echarts'
import { alarmLevelText, formatDateTime, formatNumber, formatTime, toNumber } from '@/utils/format'

/** 实时监控 4 张趋势图定义：每个指标独立一张图、独立纵轴 */
const CHART_DEFS = [
  {
    key: 'cpuUsage',
    title: 'CPU 使用率 (%)',
    color: '#2984df',
    unit: '%',
    yMax: 100,
    series: [{ key: 'cpuUsage', name: 'CPU 使用率', color: '#2984df' }]
  },
  {
    key: 'serverMemUsage',
    title: '内存使用率 (%)',
    color: '#20a86a',
    unit: '%',
    yMax: 100,
    series: [{ key: 'serverMemUsage', name: '内存使用率', color: '#20a86a' }]
  },
  {
    key: 'qps',
    title: 'QPS (次/秒)',
    color: '#7053d8',
    unit: '次/秒',
    yMax: null,
    series: [{ key: 'qps', name: 'QPS', color: '#7053d8' }]
  },
  {
    key: 'network',
    title: '网络流量 (KB/s)',
    color: '#e39b34',
    unit: 'KB/s',
    yMax: null,
    series: [
      { key: 'netInputBandwidth', name: '入', color: '#e39b34' },
      { key: 'netOutputBandwidth', name: '出', color: '#4d9de0' }
    ]
  }
]

export default {
  name: 'Dashboard',
  data() {
    return {
      instances: [],
      overview: {},
      recentAlarms: [],
      auditLogs: [],
      metricValues: {},
      metricData: {
        qps: null, latency: null, mem: null,
        qpsChange: 0, latencyChange: 0, memChange: 0
      },
      topologyNodes: [],
      topologyStatus: '--',
      trendData: {},
      chartDefs: CHART_DEFS,
      loading: false,
      trendSeq: 0
    }
  },
  computed: {
    runningCount() { return this.instances.filter(i => i.runStatus === '正常').length },
    abnormalCount() { return this.instances.filter(i => i.runStatus !== '正常').length }
  },
  created() {
    // ECharts 实例不要放进 data，避免被 Vue 2 深度观察导致图表异常
    this.charts = {}
  },
  mounted() {
    this.loadData()
    window.addEventListener('resize', this.resizeCharts)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    this.trendSeq += 1
    Object.keys(this.charts).forEach(key => {
      if (this.charts[key]) this.charts[key].dispose()
    })
    this.charts = {}
  },
  methods: {
    async loadData() {
      // 防止重复触发 loadData
      if (this.loading) return
      this.loading = true
      try {
        const [inst, ov, recent, audit] = await Promise.all([
          api.getInstances(),
          api.getRuleOverview(),
          api.getRecentAlarms(),
          // 操作日志来自平台端操作审计接口；查询失败不影响仪表盘其它数据
          api.getAuditLogs({ pageNum: 1, pageSize: 5 }).catch(() => null)
        ])
        this.instances = inst || []
        this.overview = ov || {}
        this.recentAlarms = (recent || []).slice(0, 5).map(this.normalizeAlarm)
        // 平台端分页响应统一为 { page:{ records, total, ... } }
        const auditPage = (audit && audit.page) || {}
        this.auditLogs = ((auditPage.records) || []).slice(0, 5).map(this.normalizeAudit)

        const master = this.instances.find(i => String(i.role || '').toUpperCase() === 'MASTER') || this.instances[0]
        if (!master) {
          await this.loadTrend(null)
          return
        }

        // 实例确定后，当前指标、趋势、拓扑互不依赖，并行发起；
        // 趋势图不再等待 getCurrentValues，避免指标接口异常导致图表空白。
        const currentPromise = api.getCurrentValues(master.instanceId)
          .then(cur => this.applyCurrentValues(cur))
          .catch(e => { this.$message.error(e.message || '实时指标加载失败') })
        const trendPromise = this.loadTrend(master.instanceId)
        const topologyPromise = this.loadTopology()
        await Promise.all([currentPromise, trendPromise, topologyPromise])
      } catch (e) {
        this.$message.error(e.message || '仪表盘数据加载失败')
      } finally {
        this.loading = false
      }
    },
    applyCurrentValues(cur) {
      const metrics = (cur && cur.metrics) || []
      const valueMap = {}
      metrics.forEach(m => { valueMap[m.metricKey] = m.currentValue })
      this.metricValues = valueMap
      const find = key => metrics.find(m => m.metricKey === key) || {}
      this.metricData = {
        qps: valueMap.qps,
        latency: valueMap.avgLatency,
        mem: valueMap.serverMemUsage,
        qpsChange: find('qps').changeRate5min || 0,
        latencyChange: find('avgLatency').changeRate5min || 0,
        memChange: find('serverMemUsage').changeRate5min || 0
      }
    },
    normalizeAlarm(a) {
      const name = a.instanceName || a.instanceId || ''
      const addr = a.hostIp ? `${a.hostIp}:${a.redisPort || ''}` : ''
      return {
        ...a,
        alarmLevel: Number(a.alarmLevel),
        alarmStatus: Number(a.recoverStatus),
        triggerTime: formatDateTime(a.triggerTime),
        instanceDisplay: addr ? `${name}（${addr}）` : name
      }
    },
    normalizeAudit(log) {
      const typeClassMap = {
        新增: 'green', 创建: 'green', 修改: 'yellow', 切换: 'yellow',
        删除: 'red', 导出: 'blue', 恢复: 'blue', 自动恢复: 'blue'
      }
      const type = log.operationType || log.module || '-'
      return {
        type,
        typeClass: typeClassMap[type] || 'blue',
        user: log.operatorName || log.operatorId || '-',
        content: log.operationContent || '-',
        time: formatTime(log.operationTime)
      }
    },
    resizeCharts() {
      Object.keys(this.charts).forEach(key => {
        if (this.charts[key]) this.charts[key].resize()
      })
    },
    async loadTopology() {
      try {
        const topo = await api.getTopology()
        this.topologyStatus = (topo && topo.topologyStatus) || '--'
        const nodes = (topo && topo.nodes) || []
        const master = nodes.find(n => String(n.role || '').toUpperCase() === 'MASTER')
        const replicas = nodes.filter(n => String(n.role || '').toUpperCase() !== 'MASTER')
        this.topologyNodes = []
        if (master) this.topologyNodes.push({ ...master, name: 'Master', addr: `${master.ip}:${master.port}`, role: 'master', style: { top: '8px', left: 'calc(50% - 75px)' } })
        replicas.forEach((r, i) => {
          this.topologyNodes.push({ ...r, name: 'Replica', addr: `${r.ip}:${r.port}`, role: 'replica', style: { bottom: '16px', left: i === 0 ? '2px' : 'auto', right: i === 1 ? '2px' : 'auto' } })
        })
      } catch (e) {}
    },
    async loadTrend(instanceId) {
      const seq = ++this.trendSeq
      if (!instanceId) {
        this.trendData = {}
        await this.$nextTick()
        this.renderCharts()
        return
      }
      try {
        // 趋势接口一次返回全部核心指标，前端按 metricKey 拆成 4 张图
        const data = await api.getTrend([instanceId], 'cpuUsage', '近1h')
        if (seq !== this.trendSeq) return
        this.trendData = data || {}
      } catch (e) {
        if (seq !== this.trendSeq) return
        this.trendData = {}
        this.$message.error(e.message || '实时趋势数据加载失败')
      }
      await this.$nextTick()
      if (seq !== this.trendSeq) return
      this.renderCharts()
      await this.$nextTick()
      // 首屏/布局切换后容器尺寸可能尚未稳定，再校正一次
      if (seq === this.trendSeq) this.resizeCharts()
    },
    seriesValues(metricKey) {
      const metric = (this.trendData.metricList || []).find(m => m.metricKey === metricKey)
      const points = (metric && metric.seriesList && metric.seriesList[0] && metric.seriesList[0].points) || []
      return points.map(p => toNumber(p.value))
    },
    trendTimestamps() {
      const list = this.trendData.timestampList || []
      if (list.length) return list
      // 兼容 timestampList 缺失的响应：从第一个序列的 bucket 还原横轴
      const firstMetric = (this.trendData.metricList || [])[0]
      const points = (firstMetric && firstMetric.seriesList && firstMetric.seriesList[0]
        && firstMetric.seriesList[0].points) || []
      return points.map(p => p.bucket)
    },
    renderCharts() {
      const timestamps = this.trendTimestamps()
      const xLabels = timestamps.map(t => {
        const text = String(t)
        return text.length >= 16 ? text.slice(11, 16) : text
      })
      const labelInterval = Math.max(0, Math.ceil(xLabels.length / 4) - 1)
      this.chartDefs.forEach(def => {
        // v-for 内的 ref 在 Vue 2 中统一注册为数组
        const raw = this.$refs['rtChart-' + def.key]
        const el = Array.isArray(raw) ? raw[0] : raw
        if (!el) return
        if (!this.charts[def.key]) this.charts[def.key] = echarts.init(el)
        const chart = this.charts[def.key]
        const series = def.series.map(s => {
          const values = this.seriesValues(s.key)
          const validCount = values.filter(v => v !== null && v !== undefined).length
          return {
            name: s.name,
            type: 'line',
            smooth: true,
            // 只有一个数据点时折线本身不可见，需要显示数据点
            showSymbol: validCount <= 2,
            symbolSize: 4,
            connectNulls: false,
            lineStyle: { width: 2, color: s.color },
            itemStyle: { color: s.color },
            areaStyle: def.series.length === 1 ? { color: s.color, opacity: 0.12 } : undefined,
            data: values
          }
        })
        chart.setOption({
          animation: false,
          tooltip: {
            trigger: 'axis',
            valueFormatter: value => (value === null || value === undefined ? '--' : `${formatNumber(value)}${def.unit}`)
          },
          grid: { left: 4, right: 10, top: 12, bottom: 4, containLabel: true },
          xAxis: {
            type: 'category',
            boundaryGap: false,
            data: xLabels,
            axisTick: { show: false },
            axisLine: { lineStyle: { color: '#e9edf4' } },
            axisLabel: { fontSize: 10, color: '#909399', interval: labelInterval, hideOverlap: true }
          },
          yAxis: {
            type: 'value',
            min: 0,
            max: def.yMax,
            splitNumber: 4,
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { fontSize: 10, color: '#909399' },
            splitLine: { lineStyle: { color: '#f0f3f8' } }
          },
          series
        }, true)
        // 首次布局或 CSS 尺寸尚未稳定时，setOption 后主动 resize
        chart.resize()
        this.$nextTick(() => {
          if (this.charts[def.key]) this.charts[def.key].resize()
        })
      })
    },
    legendText(def) {
      if (def.key === 'network') {
        return `当前：入 ${this.metricText('netInputBandwidth')} / 出 ${this.metricText('netOutputBandwidth')} KB/s`
      }
      return `当前：${this.metricText(def.key)}${def.unit}`
    },
    metricText(key) {
      const value = this.metricValues[key]
      return value === null || value === undefined || value === '' ? '--' : formatNumber(value)
    },
    formatNum(v) { return formatNumber(v) },
    levelText(l) { return alarmLevelText(l) }
  }
}
</script>

<style scoped>
.realtime-charts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 14px 14px 8px;
}
.rt-chart {
  min-width: 0;
  padding: 0 12px;
  border-right: 1px solid #edf0f5;
}
.rt-chart:last-child { border-right: 0; }
.rt-chart h4 {
  margin: 3px 0 8px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rt-chart-box { width: 100%; height: 205px; }
.rt-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: #7a8496;
  min-width: 0;
}
.rt-legend .dot { width: 7px; height: 7px; border-radius: 50%; flex: none; }
.rt-legend-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 1500px) {
  .realtime-charts { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: 14px; }
  .rt-chart:nth-child(2) { border-right: 0; }
  .rt-chart:nth-child(-n+2) { padding-bottom: 10px; border-bottom: 1px solid #edf0f5; }
}
@media (max-width: 1100px) {
  .realtime-charts { grid-template-columns: 1fr; }
  .rt-chart { border-right: 0; padding: 0 2px; }
  .rt-chart:nth-child(-n+2) { border-bottom: 0; padding-bottom: 0; }
}
</style>
