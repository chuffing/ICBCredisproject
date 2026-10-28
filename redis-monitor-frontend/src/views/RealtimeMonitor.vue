<template>
  <div>
    <!-- 实例选择 -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">Redis实例</div>
          <div class="panel-desc">默认展示当前Master节点，可切换查看其他Redis实例实时运行情况</div>
        </div>
      </div>
      <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
        <el-select v-model="selectedInstance" placeholder="选择实例" style="width:340px" @change="onInstanceChange">
          <el-option v-for="i in instances" :key="i.instanceId"
            :label="i.instanceName + '｜' + i.hostIp + ':' + i.redisPort + '｜' + (i.role ? i.role.toUpperCase() : '')"
            :value="i.instanceId" />
        </el-select>
        <div style="display:flex;gap:6px;align-items:center;font-size:13px">
          <span style="color:#909399">角色：</span>
          <span class="tag" :class="(currentInstance && currentInstance.role && currentInstance.role.toUpperCase())==='MASTER'?'blue':'green'">{{ (currentInstance && currentInstance.role) ? currentInstance.role.toUpperCase() : '' }}</span>
        </div>
        <div style="display:flex;gap:6px;align-items:center;font-size:13px">
          <span style="color:#909399">Redis版本：</span>
          <span>{{ (currentInstance && currentInstance.redisVersion) || '--' }}</span>
        </div>
        <div style="display:flex;gap:6px;align-items:center;font-size:13px">
          <span style="color:#909399">运行状态：</span>
          <span class="tag" :class="(currentInstance && currentInstance.runStatus)==='正常'?'green':'red'">{{ (currentInstance && currentInstance.runStatus) || '--' }}</span>
        </div>
      </div>
    </div>

    <!-- 6个指标卡片 -->
    <div class="panel">
      <div class="panel-title">核心实时指标</div>
      <div class="panel-desc">点击指标卡片可在下方查看该指标近1h或近6h变化曲线</div>
      <div class="metric-grid" style="margin-top:18px">
        <div v-for="m in metrics" :key="m.metricKey"
          class="metric-card" :class="{selected: selectedMetric===m.metricKey}"
          @click="selectMetric(m.metricKey)">
          <div class="metric-top">
            <span class="metric-name">{{ m.metricName }}</span>
            <span class="metric-status" :class="statusClass(m.metricStatus)">{{ m.metricStatus }}</span>
          </div>
          <div class="metric-value">
            <span>{{ m.currentValue != null ? m.currentValue : '--' }}</span>
            <span class="metric-unit">{{ m.unit }}</span>
          </div>
          <div class="metric-bottom">
            环比5min: {{ m.changeRate5min != null ? m.changeRate5min.toFixed(2) + '%' : '--' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 指标趋势图 -->
    <div class="panel" v-if="selectedMetric">
      <div class="panel-header">
        <div class="panel-title">{{ metricNameMap[selectedMetric] }} - 趋势</div>
        <el-radio-group v-model="timeRange" size="small" @change="loadTrend">
          <el-radio-button label="近1h"></el-radio-button>
          <el-radio-button label="近6h"></el-radio-button>
        </el-radio-group>
      </div>
      <div ref="metricChart" class="chart-container"></div>
    </div>

    <!-- Sentinel主从监控 -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">Sentinel主从监控</div>
          <div class="panel-desc">展示当前Sentinel运行状态、Redis主从关系及故障切换后的最新拓扑</div>
        </div>
        <span class="tag" :class="topologyTagClass">{{ topologyStatus }}</span>
      </div>

      <!-- Sentinel节点 -->
      <div class="sentinel-summary">
        <div v-for="s in sentinels" :key="s.sentinelId" class="sentinel-card">
          <div>
            <div class="sentinel-name">{{ s.sentinelName }}</div>
            <div class="sentinel-address">{{ s.sentinelIp }}:{{ s.sentinelPort }}</div>
          </div>
          <span class="sentinel-dot" :class="s.sentinelStatus==='在线'?'online':'offline'"></span>
        </div>
        <div v-if="!sentinels.length" class="topology-empty">暂无Sentinel节点数据</div>
      </div>

      <!-- 主从拓扑图 -->
      <div class="topology-area" v-if="topologyMaster">
        <div class="redis-node master-node">
          <div class="node-name">{{ topologyMaster.instanceName || 'Master' }}</div>
          <div class="node-address">{{ topologyMaster.ip }}:{{ topologyMaster.port }}</div>
          <div class="node-role master-role">{{ (topologyMaster.role || 'MASTER').toUpperCase() }}</div>
        </div>

        <template v-if="topologyReplicas.length">
          <!-- 从节点箭头指向主节点 -->
          <div class="replication-arrow-row">
            <div v-for="(r, i) in topologyReplicas" :key="'arrow-' + (r.instanceId || i)" class="replication-arrow-cell">
              <span class="replication-arrow">➜</span>
            </div>
          </div>
          <div class="replica-row">
            <div v-for="(r, i) in topologyReplicas" :key="r.instanceId || ('replica-' + i)" class="redis-node">
              <div class="node-name">{{ r.instanceName || 'Replica' }}</div>
              <div class="node-address">{{ r.ip }}:{{ r.port }}</div>
              <div class="node-role">REPLICA</div>
              <div class="copy-status" :class="{ error: r.copyStatus !== '正常' }">
                {{ r.copyStatus === '正常' ? '复制正常' : (r.copyStatus || '复制异常') }}
                <span v-if="r.copyDelay && r.copyDelay !== '0'"> · 延迟 {{ r.copyDelay }}s</span>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="topology-empty">当前主节点下没有从节点</div>

        <div class="topology-legend">
          <div class="legend-row"><span class="normal-line"></span>正常复制关系</div>
          <div class="legend-row"><span class="error-line"></span>异常复制关系</div>
        </div>
      </div>
      <div v-else class="topology-empty">暂无主从拓扑数据</div>

      <!-- 主从状态表 -->
      <div class="panel-title" style="height:auto;padding:0;border:none;margin:24px 0 12px">Redis主从状态</div>
      <table class="data-table">
        <thead><tr><th>Redis节点</th><th>服务器IP</th><th>Redis端口</th><th>角色</th><th>当前Master</th><th>复制状态</th><th>复制延迟</th><th>连通延迟</th><th>运行状态</th></tr></thead>
        <tbody>
          <tr v-for="n in nodesStatus" :key="n.instanceId">
            <td>{{ n.instanceName }}</td>
            <td>{{ n.ip }}</td>
            <td>{{ n.port }}</td>
            <td><span class="tag" :class="String(n.role || '').toUpperCase()==='MASTER'?'blue':'green'">{{ (n.role || '').toUpperCase() }}</span></td>
            <td>{{ n.masterName || '-' }}</td>
            <td :class="n.copyStatus==='正常'?'status-ok':'status-no'">{{ n.copyStatus || '-' }}</td>
            <td>{{ n.copyDelay != null ? n.copyDelay + 's' : '-' }}</td>
            <td>{{ n.pingLatency != null && n.pingLatency !== '' ? n.pingLatency + 'ms' : '-' }}</td>
            <td :class="n.runStatus==='正常'?'status-ok':'status-no'">{{ n.runStatus }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import * as api from '@/api'
import * as echarts from 'echarts'
import { metricStatusClass, topologyStatusClass, toNumber } from '@/utils/format'

export default {
  name: 'RealtimeMonitor',
  data() {
    return {
      instances: [],
      selectedInstance: '',
      metrics: [],
      selectedMetric: 'cpuUsage',
      timeRange: '近1h',
      sentinels: [],
      nodesStatus: [],
      topology: { nodes: [], lastSwitch: null },
      topologyStatus: '--',
      loading: false,
      trendSeq: 0,
      metricNameMap: { cpuUsage: 'CPU使用率', serverMemUsage: '服务器内存使用率', redisMemUsed: 'Redis内存使用量', qps: '吞吐量(QPS)', avgLatency: '平均延迟', netInputBandwidth: '网络入带宽', netOutputBandwidth: '网络出带宽' }
    }
  },
  computed: {
    currentInstance() { return this.instances.find(i => i.instanceId === this.selectedInstance) },
    topologyTagClass() { return topologyStatusClass(this.topologyStatus) },
    // 拓扑接口未返回节点时，回退到主从状态表数据，保证拓扑图仍可展示
    topologyNodesData() {
      const nodes = (this.topology && this.topology.nodes) || []
      return nodes.length ? nodes : (this.nodesStatus || [])
    },
    topologyMaster() {
      return this.topologyNodesData.find(n => String(n.role || '').toUpperCase() === 'MASTER') || null
    },
    topologyReplicas() {
      return this.topologyNodesData.filter(n => String(n.role || '').toUpperCase() !== 'MASTER')
    }
  },
  created() {
    // ECharts 实例不要放进 data，避免被 Vue 2 深度观察导致图表异常
    this.chart = null
  },
  mounted() {
    this.loadData()
    window.addEventListener('resize', this.resizeChart)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart)
    this.trendSeq += 1
    if (this.chart) { this.chart.dispose(); this.chart = null }
  },
  methods: {
    async loadData() {
      if (this.loading) return
      this.loading = true
      try {
        const [inst, sent, nodes, topo] = await Promise.all([
          api.getInstances(), api.getSentinels(), api.getNodesStatus(), api.getTopology().catch(() => null)
        ])
        this.instances = inst || []
        this.sentinels = sent || []
        this.nodesStatus = nodes || []
        this.topology = topo || { nodes: [], lastSwitch: null }
        this.topologyStatus = (topo && topo.topologyStatus) || '--'
        if (!this.selectedInstance && this.instances.length) {
          const master = this.instances.find(i => String(i.role || '').toUpperCase() === 'MASTER')
          this.selectedInstance = (master || this.instances[0]).instanceId
        }
        if (this.selectedInstance) {
          // 当前指标与趋势互不依赖，并行发起；趋势图不再等待指标接口
          await Promise.all([this.loadMetrics(), this.loadTrend()])
        }
      } catch (e) {
        this.$message.error(e.message || '监控数据加载失败')
      } finally {
        this.loading = false
      }
    },
    async loadMetrics() {
      if (!this.selectedInstance) return
      try {
        const data = await api.getCurrentValues(this.selectedInstance)
        const metrics = (data && data.metrics) || []
        this.metrics = metrics
        // 首次进入或切换实例后，当前选中指标不在返回列表中时重置为第一项
        if (metrics.length && !metrics.some(m => m.metricKey === this.selectedMetric)) {
          const changed = this.selectedMetric !== metrics[0].metricKey
          this.selectedMetric = metrics[0].metricKey
          if (changed) this.loadTrend()
        }
      } catch (e) {
        this.$message.error(e.message || '实时指标加载失败')
      }
    },
    onInstanceChange() {
      // 切换实例时指标和趋势都重新加载
      this.loadMetrics()
      this.loadTrend()
    },
    selectMetric(key) {
      this.selectedMetric = key
      this.loadTrend()
    },
    resizeChart() {
      if (this.chart) this.chart.resize()
    },
    async loadTrend() {
      if (!this.selectedInstance) return
      const seq = ++this.trendSeq
      // 趋势面板由 selectedMetric 控制，确保 DOM 渲染完成后再 init
      if (!this.$refs.metricChart) {
        await this.$nextTick()
      }
      const el = this.$refs.metricChart
      if (!el) return
      try {
        const data = await api.getTrend([this.selectedInstance], this.selectedMetric, this.timeRange)
        if (seq !== this.trendSeq) return
        if (!this.chart) this.chart = echarts.init(el)
        // TrendResponse.metricList 中每个指标一条记录，再按实例拆成 seriesList
        const metric = ((data && data.metricList) || []).find(m => m.metricKey === this.selectedMetric)
        const list = (metric && metric.seriesList) || []
        const multi = list.length > 1
        const label = (metric && metric.metricName) || this.metricNameMap[this.selectedMetric] || this.selectedMetric
        const series = list.map(s => {
          const name = multi ? `${label}-${(this.instances.find(i => i.instanceId === s.instanceId) || {}).instanceName || s.instanceId}` : label
          const values = ((s.points) || []).map(p => toNumber(p.value))
          const validCount = values.filter(v => v !== null && v !== undefined).length
          return {
            name,
            type: 'line',
            smooth: true,
            // 只有一个数据点时折线本身不可见，需要显示数据点
            showSymbol: validCount <= 2,
            symbolSize: 4,
            areaStyle: { opacity: 0.12 },
            data: values
          }
        })
        const timestamps = (data && data.timestampList) || []
        const xLabels = timestamps.length
          ? timestamps.map(t => String(t).slice(11, 19))
          : ((list[0] && list[0].points) || []).map(p => String(p.bucket).slice(11, 19))
        this.chart.setOption({
          animation: false,
          tooltip: { trigger: 'axis' },
          grid: { left: 60, right: 20, top: 30, bottom: 30 },
          xAxis: { type: 'category', data: xLabels, axisLabel: { fontSize: 10, color: '#909399', hideOverlap: true } },
          yAxis: { type: 'value' },
          series
        }, true)
        // 首次布局或面板刚渲染时主动校正尺寸
        this.chart.resize()
        this.$nextTick(() => { if (this.chart) this.chart.resize() })
      } catch (e) {
        if (seq !== this.trendSeq) return
        this.$message.error(e.message || '趋势数据加载失败')
      }
    },
    statusClass(s) { return metricStatusClass(s) }
  }
}
</script>

<style scoped>
.sentinel-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 25px;
}
.sentinel-card {
  border: 1px solid #ebeef5;
  border-radius: 7px;
  padding: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sentinel-name { font-size: 14px; font-weight: bold; }
.sentinel-address { font-size: 12px; color: #909399; margin-top: 5px; }
.sentinel-dot { width: 9px; height: 9px; border-radius: 50%; background: #31a354; flex: none; }
.sentinel-dot.offline { background: #f56c6c; }

/* 主从拓扑 */
.topology-area {
  width: 680px;
  max-width: 100%;
  margin: 0 auto;
  padding: 15px 0 10px;
  text-align: center;
}
.redis-node {
  width: 220px;
  padding: 14px;
  border: 1px solid #dcdfe6;
  border-radius: 7px;
  background: #fff;
  display: inline-block;
  text-align: center;
  vertical-align: top;
}
.redis-node.master-node {
  display: block;
  margin: 0 auto;
  border: 2px solid #409eff;
  background: #f0f7ff;
}
.node-name { font-size: 14px; font-weight: bold; margin-bottom: 5px; }
.node-address { font-size: 12px; color: #606266; margin-bottom: 6px; }
.node-role { font-size: 12px; color: #909399; }
.master-role { color: #409eff; }
.replication-arrow-row {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 50px;
  margin: 22px 0 5px;
}
.replication-arrow-cell { width: 220px; text-align: center; }
.replication-arrow {
  display: inline-block;
  font-size: 30px;
  line-height: 1;
  color: #909399;
  transform: rotate(-90deg);
}
.replica-row {
  display: flex;
  justify-content: center;
  gap: 50px;
  flex-wrap: wrap;
}
.copy-status { margin-top: 7px; color: #31a354; font-size: 12px; }
.copy-status.error { color: #f56c6c; }
.topology-legend {
  margin-top: 22px;
  display: flex;
  justify-content: center;
  gap: 30px;
  font-size: 12px;
  color: #606266;
}
.legend-row { display: flex; align-items: center; gap: 7px; }
.normal-line { width: 32px; border-top: 2px solid #909399; }
.error-line { width: 32px; border-top: 2px dashed #f56c6c; }
.topology-empty {
  padding: 22px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
  width: 100%;
}
.sentinel-summary .topology-empty { grid-column: 1 / -1; }
@media (max-width: 1100px) {
  .sentinel-summary { grid-template-columns: 1fr; }
  .replica-row { gap: 20px; }
  .replication-arrow-row { gap: 20px; }
  .replication-arrow-cell, .redis-node { width: 180px; }
}
</style>
