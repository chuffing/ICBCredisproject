<template>
  <div>
    <!-- 概览统计 -->
    <div class="summary-grid">
      <div class="summary-card">
        <div class="summary-title">启用规则</div>
        <div class="summary-value blue">{{ overview.enableRuleCount || 0 }}</div>
        <div class="summary-desc">当前正在生效的告警规则</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">当前未恢复告警</div>
        <div class="summary-value red">{{ overview.unrecoveredAlarmCount || 0 }}</div>
        <div class="summary-desc">当前仍处于异常状态</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">今日告警</div>
        <div class="summary-value orange">{{ overview.todayAlarmTotal || 0 }}</div>
        <div class="summary-desc">
          严重{{ (overview.todayAlarmLevelCount && overview.todayAlarmLevelCount.severity) || 0 }}
          ｜警告{{ (overview.todayAlarmLevelCount && overview.todayAlarmLevelCount.alarm) || 0 }}
          ｜提示{{ (overview.todayAlarmLevelCount && overview.todayAlarmLevelCount.info) || 0 }}
        </div>
      </div>
      <div class="summary-card">
        <div class="summary-title">告警检测状态</div>
        <div class="summary-value green" style="font-size:20px">{{ overview.detectTaskStatus || '运行正常' }}</div>
        <div class="summary-desc">后台每5min自动执行检测</div>
      </div>
    </div>

    <!-- 告警规则列表 -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">告警规则配置</div>
          <div class="panel-desc">配置Redis监控指标的告警阈值、持续时长、告警级别和静默期</div>
        </div>
        <div style="display:flex;gap:10px">
          <el-button size="small" @click="triggerAll">全量手动检测</el-button>
          <el-button size="small" type="primary" @click="openAdd">+新增规则</el-button>
        </div>
      </div>
      <!-- 查询条件 -->
      <div class="query-row">
        <el-input v-model="query.keyword" placeholder="规则名称/监控指标" style="width:220px" clearable />
        <el-select v-model="query.alarmLevel" placeholder="全部级别" clearable style="width:140px">
          <el-option label="严重" :value="3" />
          <el-option label="警告" :value="2" />
          <el-option label="提示" :value="1" />
        </el-select>
        <el-select v-model="query.enableStatus" placeholder="全部状态" clearable style="width:140px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <!-- 规则表格 -->
      <el-table :data="rules" border size="small" style="width:100%">
        <el-table-column prop="ruleName" label="规则名称" min-width="150" />
        <el-table-column prop="metricName" label="监控指标" width="130" />
        <el-table-column prop="compareType" label="比较方式" width="85" />
        <el-table-column label="阈值" width="95">
          <template slot-scope="s">{{ s.row.threshold }}{{ s.row.unit }}</template>
        </el-table-column>
        <el-table-column label="持续时长" width="95">
          <template slot-scope="s">{{ durationText(s.row) }}</template>
        </el-table-column>
        <el-table-column label="告警级别" width="90">
          <template slot-scope="s"><span class="tag" :class="s.row.alarmLevel===3?'red':s.row.alarmLevel===2?'yellow':'blue'">{{ levelText(s.row.alarmLevel) }}</span></template>
        </el-table-column>
        <el-table-column label="静默期" width="85">
          <template slot-scope="s">{{ silenceText(s.row) }}</template>
        </el-table-column>
        <el-table-column label="生效节点" min-width="110">
          <template slot-scope="s">{{ scopeText(s.row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template slot-scope="s">
            <el-switch :value="s.row.enableStatus===1" @change="toggleStatus(s.row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template slot-scope="s">
            <el-button type="text" size="small" @click="triggerCheck(s.row.ruleId)">手动检测</el-button>
            <el-button type="text" size="small" @click="editRule(s.row)">编辑</el-button>
            <el-button type="text" size="small" style="color:#f56c6c" @click="deleteRule(s.row.ruleId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:16px;text-align:right" layout="prev, pager, next, total"
        :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
    </div>

    <!-- 当前未恢复告警（位于告警规则下方） -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">当前未恢复告警</div>
          <div class="panel-desc">展示当前仍满足告警条件的Redis异常</div>
        </div>
        <span class="more-link" @click="goRecords">查看全部</span>
      </div>
      <div class="alert-list" v-if="unrecovered.length">
        <div class="alert-row alert-head">
          <div>告警级别</div><div>Redis节点</div><div>告警内容</div><div>触发值</div><div>触发时间</div><div>状态</div>
        </div>
        <div class="alert-row" v-for="a in unrecovered" :key="a.alarmRecordId || a.alarmNo">
          <div><span class="tag" :class="a.alarmLevel===3?'red':a.alarmLevel===2?'yellow':'blue'">{{ levelText(a.alarmLevel) }}</span></div>
          <div :title="a.instanceDisplay">{{ a.instanceDisplay }}</div>
          <div :title="a.alarmContent">{{ a.alarmContent }}</div>
          <div :title="(a.triggerValue || '') + (a.unit || '')">{{ a.triggerValue }}{{ a.unit }}</div>
          <div>{{ a.triggerTime }}</div>
          <div><span class="tag red">未恢复</span></div>
        </div>
      </div>
      <div v-else class="alert-empty">暂无未恢复告警</div>
    </div>

    <!-- 最近告警（位于告警规则下方） -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">最近告警</div>
          <div class="panel-desc">简要展示最近产生的告警，完整历史信息在告警记录页面查看</div>
        </div>
        <span class="more-link" @click="goRecords">查看全部</span>
      </div>
      <div class="alert-list" v-if="recentAlarms.length">
        <div class="alert-row alert-head">
          <div>告警级别</div><div>Redis节点</div><div>告警内容</div><div>触发值</div><div>触发时间</div><div>状态</div>
        </div>
        <div class="alert-row" v-for="a in recentAlarms" :key="a.alarmRecordId || a.alarmNo">
          <div><span class="tag" :class="a.alarmLevel===3?'red':a.alarmLevel===2?'yellow':'blue'">{{ levelText(a.alarmLevel) }}</span></div>
          <div :title="a.instanceDisplay">{{ a.instanceDisplay }}</div>
          <div :title="a.alarmContent">{{ a.alarmContent }}</div>
          <div :title="(a.triggerValue || '') + (a.unit || '')">{{ a.triggerValue }}{{ a.unit }}</div>
          <div>{{ a.triggerTime }}</div>
          <div><span class="tag" :class="a.recoverStatus===0?'red':'green'">{{ a.recoverStatus===0?'未恢复':'已恢复' }}</span></div>
        </div>
      </div>
      <div v-else class="alert-empty">暂无告警</div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="editId?'编辑告警规则':'新增告警规则'" :visible.sync="showAdd" width="600px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="规则名称"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="监控指标">
          <el-select v-model="form.metricKey" style="width:100%">
            <el-option label="CPU使用率" value="cpuUsage" />
            <el-option label="服务器内存使用率" value="serverMemUsage" />
            <el-option label="Redis内存使用量" value="redisMemUsed" />
            <el-option label="QPS" value="qps" />
            <el-option label="平均延迟" value="avgLatency" />
            <el-option label="网络输入带宽" value="netInputBandwidth" />
            <el-option label="网络输出带宽" value="netOutputBandwidth" />
            <el-option label="节点存活状态" value="nodeRunStatus" />
            <el-option label="哨兵状态" value="sentinelStatus" />
            <el-option label="主从复制延迟" value="replDelay" />
            <el-option label="连通性延迟" value="pingLatency" />
          </el-select>
        </el-form-item>
        <el-form-item label="比较方式">
          <el-select v-model="form.compareType" style="width:100%">
            <el-option label=">" value=">" /><el-option label="<" value="<" />
            <el-option label=">=" value=">=" /><el-option label="<=" value="<=" /><el-option label="=" value="=" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警阈值"><el-input v-model="form.threshold" /></el-form-item>
        <el-form-item label="阈值单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="持续周期数"><el-input v-model.number="form.duration" /></el-form-item>
        <el-form-item label="告警级别">
          <el-radio-group v-model="form.alarmLevel">
            <el-radio :label="1">提示</el-radio><el-radio :label="2">警告</el-radio><el-radio :label="3">严重</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="静默期(s)"><el-input v-model.number="form.silenceDuration" /></el-form-item>
        <el-form-item label="生效范围">
          <el-radio-group v-model="form.scopeType">
            <el-radio :label="1">全部节点</el-radio><el-radio :label="2">指定节点</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生效节点" v-if="form.scopeType===2">
          <el-select v-model="form.scopeInstanceIds" multiple style="width:100%">
            <el-option v-for="i in instances" :key="i.instanceId" :label="i.instanceName" :value="i.instanceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enableStatus" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showAdd=false">取消</el-button>
        <el-button type="primary" @click="saveRule">确定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import * as api from '@/api'
import { alarmLevelText, formatDateTime } from '@/utils/format'

export default {
  name: 'AlarmRules',
  data() {
    return {
      overview: {},
      unrecovered: [],
      recentAlarms: [],
      rules: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      query: { keyword: '', alarmLevel: null, enableStatus: null },
      showAdd: false,
      editId: null,
      saving: false,
      loading: false,
      instances: [],
      // 必须与后端 CommonConstant.METRIC_NAMES 完全一致，修改接口会按枚举校验 metricName
      metricNameMap: {
        cpuUsage: 'CPU使用率',
        serverMemUsage: '服务器内存使用率',
        redisMemUsed: 'Redis内存使用量',
        qps: 'QPS',
        avgLatency: '平均延迟',
        netInputBandwidth: '网络输入带宽',
        netOutputBandwidth: '网络输出带宽',
        nodeRunStatus: '节点存活状态',
        sentinelStatus: '哨兵状态',
        replDelay: '主从复制延迟',
        pingLatency: '连通性延迟'
      },
      form: this.emptyForm()
    }
  },
  mounted() { this.loadData() },
  methods: {
    emptyForm() {
      return {
        ruleName: '',
        metricKey: '',
        compareType: '>',
        threshold: '',
        unit: '',
        // 平台端 duration 单位为「采集周期个数」，collectInterval 单位秒
        duration: 1,
        collectInterval: 300,
        alarmLevel: 2,
        silenceDuration: 1800,
        scopeType: 1,
        scopeInstanceIds: [],
        enableStatus: 1
      }
    },
    // 页面数据加载入口
    async loadData() {
      if (this.loading) return
      this.loading = true
      try {
        // 规则列表查询必须立即发起，不能被概览接口的耗时或异常阻断
        const rulesPromise = this.loadRules()
        let firstError = null
        const safe = promise => promise.catch(e => {
          if (!firstError) firstError = e
          return null
        })
        const [ov, un, recent, inst] = await Promise.all([
          safe(api.getRuleOverview()),
          safe(api.getRecentUnrecovered()),
          safe(api.getRecentAlarms()),
          safe(api.getInstances())
        ])
        this.overview = ov || {}
        this.unrecovered = (un || []).map(this.normalizeAlarm)
        this.recentAlarms = (recent || []).map(this.normalizeAlarm)
        this.instances = inst || []
        if (firstError) this.$message.error(firstError.message || '告警概览加载失败')
        await rulesPromise
      } finally {
        this.loading = false
      }
    },
    async loadRules() {
      const params = {
        pageNum: this.pageNum,
        pageSize: this.pageSize
      }
      // 可选筛选条件为空时不传，避免后端对 null 的可选字段做非空校验
      if (this.query.keyword) params.keyword = this.query.keyword
      if (this.query.alarmLevel !== null && this.query.alarmLevel !== '') params.alarmLevel = this.query.alarmLevel
      if (this.query.enableStatus !== null && this.query.enableStatus !== '') params.enableStatus = this.query.enableStatus
      try {
        const data = await api.getRuleList(params)
        // 平台端分页响应为 { scopeInstanceNames, page:{ records, total, ... } }
        const page = (data && data.page) || {}
        this.rules = (page.records || []).map(this.normalizeRule)
        this.total = page.total || 0
      } catch (e) {
        this.rules = []
        this.total = 0
        this.$message.error(e.message || '告警规则加载失败')
      }
    },
    search() {
      this.pageNum = 1
      this.loadRules()
    },
    resetQuery() {
      this.query = { keyword: '', alarmLevel: null, enableStatus: null }
      this.pageNum = 1
      this.loadRules()
    },
    normalizeAlarm(a) {
      // 未恢复/最近告警列表的 Redis节点 字段只展示实例名 instanceName
      const name = a.instanceName || a.instanceId || ''
      return {
        ...a,
        alarmLevel: Number(a.alarmLevel),
        recoverStatus: String(a.recoverStatus) === '0' ? 0 : 1,
        triggerTime: formatDateTime(a.triggerTime),
        instanceDisplay: name || '-'
      }
    },
    normalizeRule(r) {
      const enabled = r.enableStatus === true || Number(r.enableStatus) === 1
      return {
        ...r,
        metricName: r.metricName || this.metricNameMap[r.metricKey] || r.metricKey,
        alarmLevel: Number(r.alarmLevel),
        scopeType: Number(r.scopeType) || 1,
        enableStatus: enabled ? 1 : 0,
        scopeInstanceIds: r.scopeInstanceId
          ? String(r.scopeInstanceId).split(',').map(id => id.trim()).filter(Boolean)
          : []
      }
    },
    onPageChange(p) { this.pageNum = p; this.loadRules() },
    durationText(row) {
      const periods = Number(row.duration)
      const interval = Number(row.collectInterval) || 300
      if (!periods) return '-'
      return `${Math.round(periods * interval / 60)}min`
    },
    silenceText(row) {
      const seconds = Number(row.silenceDuration)
      if (!seconds) return '-'
      return `${Math.round(seconds / 60)}min`
    },
    scopeText(row) {
      if (row.scopeType === 1) return '全部Redis'
      const names = (row.scopeInstanceNames && row.scopeInstanceNames.length)
        ? row.scopeInstanceNames
        : (row.scopeInstanceIds || []).map(id => {
          const instance = this.instances.find(i => i.instanceId === id)
          return instance ? instance.instanceName : id
        })
      return names.length ? names.join('、') : '指定节点'
    },
    goRecords() { this.$router.push('/alarm-records') },
    async toggleStatus(row) {
      try {
        // 平台端 enableStatus 为字符串枚举 "0"/"1"
        await api.toggleRuleStatus(row.ruleId, row.enableStatus === 1 ? '0' : '1')
        this.$message.success('状态已切换')
        this.loadData()
      } catch (e) {
        this.$message.error(e.message || '操作失败')
      }
    },
    async triggerCheck(ruleId) {
      try {
        const message = await api.triggerCheck(ruleId)
        this.$message.success(message || '已触发手动检测')
      } catch (e) {
        this.$message.error(e.message || '操作失败')
      }
    },
    async triggerAll() {
      try {
        const message = await api.triggerAllCheck()
        this.$message.success(message || '已触发全量检测')
      } catch (e) {
        this.$message.error(e.message || '操作失败')
      }
    },
    openAdd() {
      this.editId = null
      this.form = this.emptyForm()
      this.showAdd = true
    },
    editRule(row) {
      this.editId = row.ruleId
      this.form = {
        ruleId: row.ruleId,
        ruleName: row.ruleName,
        metricKey: row.metricKey,
        compareType: row.compareType,
        threshold: row.threshold,
        unit: row.unit,
        duration: row.duration ? Number(row.duration) : 1,
        collectInterval: row.collectInterval ? Number(row.collectInterval) : 300,
        alarmLevel: Number(row.alarmLevel) || 2,
        silenceDuration: row.silenceDuration ? Number(row.silenceDuration) : 1800,
        scopeType: Number(row.scopeType) || 1,
        scopeInstanceIds: row.scopeInstanceIds || [],
        enableStatus: Number(row.enableStatus) === 1 ? 1 : 0
      }
      this.showAdd = true
    },
    async saveRule() {
      if (this.saving) return
      if (!String(this.form.ruleName || '').trim()) { this.$message.warning('请填写规则名称'); return }
      if (!this.form.metricKey) { this.$message.warning('请选择监控指标'); return }
      const threshold = Number(this.form.threshold)
      if (this.form.threshold === '' || this.form.threshold === null || Number.isNaN(threshold)) {
        this.$message.warning('请填写有效的告警阈值')
        return
      }
      if (this.form.scopeType === 2 && !(this.form.scopeInstanceIds || []).length) {
        this.$message.warning('请选择生效节点')
        return
      }
      this.saving = true
      try {
        const data = {
          ruleName: String(this.form.ruleName).trim(),
          metricKey: this.form.metricKey,
          // 修改接口要求 metricName 为合法枚举值；新增接口会按 metricKey 覆盖
          metricName: this.metricNameMap[this.form.metricKey] || this.form.metricKey,
          compareType: this.form.compareType,
          threshold,
          // unit 平台允许空串，但不能省略（校验 notNull）
          unit: this.form.unit || '',
          collectInterval: String(this.form.collectInterval || 300),
          duration: String(this.form.duration || 1),
          alarmLevel: String(this.form.alarmLevel),
          silenceDuration: String(this.form.silenceDuration || 0),
          scopeType: String(this.form.scopeType),
          scopeInstanceIds: this.form.scopeType === 2 ? this.form.scopeInstanceIds : [],
          // 平台端为字符串枚举 "0"/"1"，传 boolean 会被 Jackson 转成 "true"/"false" 而校验失败
          enableStatus: Number(this.form.enableStatus) === 1 ? '1' : '0'
        }
        if (this.editId) {
          data.ruleId = this.editId
          await api.updateRule(data)
        } else {
          await api.addRule(data)
        }
        this.$message.success('保存成功')
        this.showAdd = false
        this.editId = null
        this.loadData()
      } catch (e) {
        this.$message.error('保存失败: ' + (e.message || ''))
      } finally {
        this.saving = false
      }
    },
    async deleteRule(ruleId) {
      try {
        await this.$confirm('确定删除该规则?', '提示', { type: 'warning' })
      } catch (e) {
        return
      }
      try {
        await api.deleteRule(ruleId)
        this.$message.success('删除成功')
        // 删除最后一条时回到上一页，避免停留在空页
        if (this.rules.length === 1 && this.pageNum > 1) this.pageNum -= 1
        this.loadData()
      } catch (e) {
        this.$message.error(e.message || '删除失败')
      }
    },
    levelText(l) { return alarmLevelText(l) }
  }
}
</script>

<style scoped>
.more-link { color: #2784ed; font-size: 12px; cursor: pointer; }
.alert-list {
  border: 1px solid #ebeef5;
  border-radius: 7px;
  overflow-x: auto;
}
.alert-row {
  display: grid;
  grid-template-columns: 100px 175px minmax(220px, 1fr) 110px 165px 100px;
  align-items: center;
  gap: 10px;
  min-width: 870px;
  padding: 13px 15px;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
}
.alert-row:last-child { border-bottom: none; }
.alert-row > div {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alert-row:not(.alert-head):hover { background: #f8fbff; }
.alert-head {
  background: #fafafa;
  color: #606266;
  font-weight: bold;
}
.alert-empty {
  padding: 26px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
  border: 1px solid #ebeef5;
  border-radius: 7px;
}
</style>
