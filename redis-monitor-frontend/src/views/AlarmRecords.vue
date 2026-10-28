<template>
  <div>
    <!-- 统计概览（最近3天） -->
    <div class="summary-grid">
      <div class="summary-card">
        <div class="summary-title">告警总数</div>
        <div class="summary-value blue">{{ overview.alarmTotal || 0 }}</div>
        <div class="summary-desc">最近3天数据</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">未恢复</div>
        <div class="summary-value red">{{ overview.unrecoveredCount || 0 }}</div>
        <div class="summary-desc">最近3天数据</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">已恢复</div>
        <div class="summary-value green">{{ overview.recoveredCount || 0 }}</div>
        <div class="summary-desc">最近3天数据</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">分级统计</div>
        <div class="summary-desc" style="margin-top:4px">
          <span class="tag blue">提示:{{ (overview.alarmLevelCount && overview.alarmLevelCount.info) || 0 }}</span>
          <span class="tag yellow" style="margin-left:6px">警告:{{ (overview.alarmLevelCount && overview.alarmLevelCount.alarm) || 0 }}</span>
          <span class="tag red" style="margin-left:6px">严重:{{ (overview.alarmLevelCount && overview.alarmLevelCount.severity) || 0 }}</span>
        </div>
      </div>
    </div>

    <!-- 查询条件 -->
    <div class="panel">
      <div class="query-row">
        <el-select v-model="query.instanceId" placeholder="节点实例" clearable style="width:200px">
          <el-option v-for="i in instances" :key="i.instanceId" :label="i.instanceName" :value="i.instanceId" />
        </el-select>
        <el-select v-model="query.alarmLevel" placeholder="告警级别" clearable style="width:140px">
          <el-option label="提示" :value="1" />
          <el-option label="警告" :value="2" />
          <el-option label="严重" :value="3" />
        </el-select>
        <el-select v-model="query.metricKey" placeholder="监控指标" clearable style="width:160px">
          <el-option label="CPU使用率" value="cpuUsage" />
          <el-option label="内存使用率" value="serverMemUsage" />
          <el-option label="Redis内存使用量" value="redisMemUsed" />
          <el-option label="QPS" value="qps" />
          <el-option label="延迟" value="avgLatency" />
          <el-option label="网络入带宽" value="netInputBandwidth" />
          <el-option label="网络出带宽" value="netOutputBandwidth" />
        </el-select>
        <el-select v-model="query.recoverStatus" placeholder="处理状态" clearable style="width:140px">
          <el-option label="未恢复" :value="0" />
          <el-option label="已恢复" :value="1" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="关键字(编号/内容)" clearable style="width:200px" />
        <el-date-picker v-model="dateRange" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" style="width:360px" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </div>

    <!-- 告警记录表格 -->
    <div class="panel">
      <el-table :data="records" border size="small" style="width:100%">
        <el-table-column prop="alarmNo" label="告警编号" width="140" />
        <el-table-column prop="instanceDisplay" label="实例" min-width="180" />
        <el-table-column prop="metricName" label="监控指标" width="120" />
        <el-table-column label="级别" width="80">
          <template slot-scope="s"><span class="tag" :class="s.row.alarmLevel===3?'red':s.row.alarmLevel===2?'yellow':'blue'">{{ levelText(s.row.alarmLevel) }}</span></template>
        </el-table-column>
        <el-table-column prop="alarmContent" label="告警内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="triggerValue" label="触发值" width="90" />
        <el-table-column prop="threshold" label="阈值" width="80" />
        <el-table-column prop="triggerTime" label="触发时间" width="160" />
        <el-table-column prop="recoverTime" label="恢复时间" width="160">
          <template slot-scope="s">{{ s.row.recoverTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template slot-scope="s"><span class="tag" :class="s.row.recoverStatus===0?'red':'green'">{{ s.row.recoverStatus===0?'未恢复':'已恢复' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template slot-scope="s">
            <el-button type="text" size="small" @click="showDetail(s.row)">详情</el-button>
            <el-button type="text" size="small" :disabled="s.row.recoverStatus===1" @click="recoverAlarm(s.row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:16px;text-align:right" layout="prev, pager, next, total"
        :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
    </div>

    <!-- 详情弹窗（详情与指标曲线同步展示） -->
    <el-dialog title="告警详情" :visible.sync="detailVisible" width="860px"
      @opened="renderDetailChart" @closed="disposeDetailChart">
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border size="small" v-if="detail">
          <el-descriptions-item label="告警编号">{{ (detail.alarmBaseInfo && detail.alarmBaseInfo.alarmNo) }}</el-descriptions-item>
          <el-descriptions-item label="告警级别">{{ levelText((detail.alarmBaseInfo && detail.alarmBaseInfo.alarmLevel)) }}</el-descriptions-item>
          <el-descriptions-item label="触发值">{{ (detail.alarmBaseInfo && detail.alarmBaseInfo.triggerValue) }}</el-descriptions-item>
          <el-descriptions-item label="阈值">{{ (detail.alarmBaseInfo && detail.alarmBaseInfo.threshold) }}</el-descriptions-item>
          <el-descriptions-item label="触发时间">{{ (detail.alarmBaseInfo && detail.alarmBaseInfo.triggerTime) }}</el-descriptions-item>
          <el-descriptions-item label="恢复时间">{{ (detail.alarmBaseInfo && detail.alarmBaseInfo.recoverTime) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="告警内容" :span="2">{{ (detail.alarmBaseInfo && detail.alarmBaseInfo.alarmContent) }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ (detail.ruleInfo && detail.ruleInfo.ruleName) }}</el-descriptions-item>
          <el-descriptions-item label="比较方式">{{ (detail.ruleInfo && detail.ruleInfo.compareType) }}</el-descriptions-item>
          <el-descriptions-item label="实例名称">{{ (detail.instanceInfo && detail.instanceInfo.instanceName) }}</el-descriptions-item>
          <el-descriptions-item label="实例地址">{{ (detail.instanceInfo && detail.instanceInfo.hostIp) }}:{{ (detail.instanceInfo && detail.instanceInfo.redisPort) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section-title">告警关联指标波动曲线</div>
        <div ref="detailChart" style="height:340px"></div>
        <div v-if="!detailTrend || !detailTrend.valueList || !detailTrend.valueList.length" class="chart-empty">暂无指标曲线数据</div>
      </div>
      <span slot="footer">
        <el-button @click="detailVisible=false">关闭</el-button>
        <el-button v-if="detail && detail.alarmBaseInfo && String(detail.alarmBaseInfo.recoverStatus)==='0'"
          type="primary" :loading="recovering" @click="recoverAlarm(detail.alarmBaseInfo)">确认恢复</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import * as api from '@/api'
import * as echarts from 'echarts'
import { alarmLevelText, formatDateTime, toNumber } from '@/utils/format'

export default {
  name: 'AlarmRecords',
  data() {
    return {
      overview: {},
      instances: [],
      records: [],
      total: 0,
      pageNum: 1,
      pageSize: 12,
      query: { instanceId: '', alarmLevel: null, metricKey: '', recoverStatus: null, keyword: '' },
      dateRange: [],
      loading: false,
      detailVisible: false,
      detailLoading: false,
      detail: null,
      detailTrend: null,
      detailRecordId: '',
      recovering: false
    }
  },
  created() {
    // ECharts 实例不要放进 data，避免被 Vue 2 深度观察导致图表异常
    this.detailChart = null
  },
  mounted() { this.loadData() },
  beforeDestroy() { this.disposeDetailChart() },
  methods: {
    // 页面数据加载入口
    // 进入页面即并发触发告警概览、实例列表和告警记录查询，避免被前置接口阻塞
    async loadData() {
      if (this.loading) return
      this.loading = true
      try {
        await Promise.all([this.loadOverview(), this.loadInstances(), this.loadRecords()])
      } finally {
        this.loading = false
      }
    },
    async loadInstances() {
      try {
        this.instances = (await api.getInstances()) || []
      } catch (e) { this.$message.error(e.message || '实例列表加载失败') }
    },
    dateParams() {
      if (this.dateRange && this.dateRange.length === 2) {
        return [this.formatDate(this.dateRange[0]), this.formatDate(this.dateRange[1])]
      }
      return [undefined, undefined]
    },
    async loadOverview() {
      const [startTime, endTime] = this.dateParams()
      try {
        const ov = await api.getRecordOverview(startTime, endTime)
        const data = ov || {}
        // 平台端 RecordOverviewResponse 为扁平字段，这里组装成页面使用的分级结构
        this.overview = {
          ...data,
          alarmLevelCount: {
            info: data.infoCount || 0,
            alarm: data.alarmCount || 0,
            severity: data.severityCount || 0
          }
        }
      } catch (e) {
        this.$message.error(e.message || '告警概览加载失败')
      }
    },
    async loadRecords() {
      const [startTime, endTime] = this.dateParams()
      const params = {
        instanceId: this.query.instanceId,
        alarmLevel: this.query.alarmLevel,
        metricKey: this.query.metricKey,
        recoverStatus: this.query.recoverStatus,
        keyword: this.query.keyword,
        pageNum: this.pageNum,
        pageSize: this.pageSize
      }
      if (startTime) {
        params.startTime = startTime
        params.endTime = endTime
      }
      try {
        const data = await api.getRecordPage(params)
        // 平台端分页响应为 { page:{ records, total, current, size } }
        const page = (data && data.page) || {}
        this.records = (page.records || []).map(this.normalizeRecord)
        this.total = page.total || 0
      } catch (e) {
        this.$message.error(e.message || '告警记录加载失败')
      }
    },
    search() {
      this.pageNum = 1
      this.loadOverview()
      this.loadRecords()
    },
    resetQuery() {
      this.query = { instanceId: '', alarmLevel: null, metricKey: '', recoverStatus: null, keyword: '' }
      this.dateRange = []
      this.pageNum = 1
      this.loadOverview()
      this.loadRecords()
    },
    normalizeRecord(r) {
      // 列表实例字段只展示实例名 instanceName
      const name = r.instanceName || r.instanceId || ''
      return {
        ...r,
        // 平台端详情/曲线接口按告警记录id查询，alarmNo 仅用于展示
        alarmRecordId: r.alarmRecordId || '',
        alarmLevel: Number(r.alarmLevel),
        recoverStatus: String(r.recoverStatus) === '0' ? 0 : 1,
        triggerTime: formatDateTime(r.triggerTime),
        recoverTime: r.recoverTime ? formatDateTime(r.recoverTime) : '',
        instanceDisplay: name || '-'
      }
    },
    onPageChange(p) { this.pageNum = p; this.loadRecords() },
    recordIdOf(row) {
      if (!row) return ''
      return row.alarmRecordId || row.id || this.detailRecordId || ''
    },
    async showDetail(row) {
      const recordId = this.recordIdOf(row)
      if (!recordId) { this.$message.error('缺少告警记录ID，无法查询详情'); return }
      this.detailRecordId = recordId
      this.detailLoading = true
      // 先打开弹窗显示 loading，避免详情接口慢时点击后没有反馈
      this.detail = null
      this.detailTrend = null
      this.detailVisible = true
      // 详情和曲线并行请求；详情先返回时先展示详情，曲线返回后再补画
      const trendPromise = api.getMetricTrend(recordId).catch(e => {
        this.$message.warning(e.message || '指标曲线加载失败')
        return null
      })
      try {
        const data = await api.getRecordDetail(recordId)
        if (data && data.alarmBaseInfo) {
          data.alarmBaseInfo.triggerTime = formatDateTime(data.alarmBaseInfo.triggerTime)
          data.alarmBaseInfo.recoverTime = data.alarmBaseInfo.recoverTime
            ? formatDateTime(data.alarmBaseInfo.recoverTime)
            : ''
        }
        this.detail = data || {}
        // 详情弹窗渲染完成后先绘制空图，曲线返回后再更新
        this.$nextTick(this.renderDetailChart)
        this.detailLoading = false

        const trend = await trendPromise
        this.detailTrend = trend || null
        this.$nextTick(this.renderDetailChart)
      } catch (e) {
        this.$message.error(e.message || '获取详情失败')
      } finally {
        this.detailLoading = false
      }
    },
    renderDetailChart() {
      if (!this.$refs.detailChart) return
      if (!this.detailChart) this.detailChart = echarts.init(this.$refs.detailChart)
      const data = this.detailTrend || {}
      const threshold = toNumber(data.thresholdValue)
      this.detailChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 60, right: 24, top: 34, bottom: 34 },
        xAxis: {
          type: 'category',
          data: ((data.timestampList) || []).map(t => formatDateTime(t, '')),
          axisLabel: { fontSize: 10, color: '#909399', hideOverlap: true }
        },
        yAxis: {
          type: 'value',
          name: data.unit || '',
          nameTextStyle: { fontSize: 10, color: '#909399' },
          axisLabel: { fontSize: 10, color: '#909399' },
          splitLine: { lineStyle: { color: '#f0f3f8' } }
        },
        series: [{
          name: data.metricName || '指标',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: (data.valueList) || [],
          lineStyle: { width: 2, color: '#409eff' },
          itemStyle: { color: '#409eff' },
          areaStyle: { color: '#409eff', opacity: 0.12 },
          markLine: threshold === null ? undefined : {
            silent: true,
            symbol: 'none',
            lineStyle: { color: '#f56c6c', type: 'dashed' },
            data: [{ yAxis: threshold, name: '阈值' }]
          }
        }]
      }, true)
      this.detailChart.resize()
    },
    disposeDetailChart() {
      if (this.detailChart) {
        this.detailChart.dispose()
        this.detailChart = null
      }
    },
    async recoverAlarm(row) {
      const recordId = this.recordIdOf(row)
      if (!recordId) { this.$message.error('缺少告警记录ID，无法恢复'); return }
      try {
        await this.$confirm('确认将该告警标记为已恢复？', '提示', { type: 'warning' })
      } catch (e) {
        return
      }
      this.recovering = true
      try {
        const result = await api.recoverRecord(recordId)
        if (result === false) {
          this.$message.error('恢复失败')
          return
        }
        this.$message.success('告警已恢复')
        await Promise.all([this.loadOverview(), this.loadRecords()])
        // 详情弹窗打开时同步刷新详情和曲线
        if (this.detailVisible && this.detailRecordId === recordId) {
          await this.showDetail({ alarmRecordId: recordId })
        }
      } catch (e) {
        this.$message.error(e.message || '恢复失败')
      } finally {
        this.recovering = false
      }
    },
    formatDate(d) {
      const p = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
    },
    levelText(l) { return alarmLevelText(l) }
  }
}
</script>

<style scoped>
.detail-section-title {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #253149;
}
.chart-empty {
  margin-top: -22px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}
</style>
