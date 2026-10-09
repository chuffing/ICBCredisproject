<template>
  <section v-if="visible" class="panel task-panel">
    <div class="section-heading compact">
      <div><h2>任务执行进度</h2><p>页面每 1.5 秒从平台读取一次最新步骤结果。</p></div>
      <span class="badge" :class="`badge-${taskMeta.tone}`">{{ taskMeta.label }}</span>
    </div>
    <div class="task-summary">
      <div class="task-identity"><strong>{{ detail.task.taskNo }}</strong><span>开始时间 {{ formatDateTime(detail.task.startTime || detail.task.createTime) }}</span></div>
      <div class="task-progress">
        <div class="progress-track"><div class="progress-bar" :class="progressClass" :style="{ width: `${percent}%` }"></div></div>
        <div class="progress-label"><span>{{ completed }} / {{ steps.length }} 个节点步骤已结束</span><span>{{ percent }}%</span></div>
      </div>
    </div>
    <div class="step-list">
      <div v-for="step in steps" :key="step.id" class="step-row">
        <small>步骤 {{ step.stepNo }}</small>
        <span class="step-operation">{{ operationLabel(step.operationType) }}</span>
        <span class="step-node">{{ agentLabel(step.executorId) }}</span>
        <span class="badge" :class="`badge-${stepMeta(step.stepStatus).tone}`">{{ stepMeta(step.stepStatus).label }}</span>
      </div>
    </div>
    <div v-if="detail.task.failureReason" class="error-line">{{ detail.task.failureReason }}</div>
  </section>
</template>

<script>
import { state } from "../store/appStore";

const OPERATION_LABELS = {
  PRECHECK_HOST: "服务器预检查",
  DEPLOY_VERSION: "部署版本包",
  STOP_SENTINEL: "停止 Sentinel",
  STOP_REDIS: "停止 Redis",
  RESTORE_CONFIG: "恢复备份配置",
  DEPLOY_CONFIG: "下发配置",
  SWITCH_VERSION: "切换程序版本",
  START_REDIS: "启动 Redis",
  START_SENTINEL: "启动 Sentinel",
  ROLLBACK_VERSION: "回滚程序版本"
};

const TASK_STATUS = {
  CREATED: { label: "等待调度", tone: "neutral" },
  RUNNING: { label: "执行中", tone: "warning" },
  ROLLING_BACK: { label: "自动回滚中", tone: "warning" },
  SUCCESS: { label: "执行成功", tone: "success" },
  FAILED: { label: "执行失败", tone: "danger" },
  ROLLED_BACK: { label: "已自动回滚", tone: "danger" }
};

const STEP_STATUS = {
  CREATED: { label: "等待", tone: "neutral" },
  DISPATCHED: { label: "执行中", tone: "warning" },
  SUCCESS: { label: "成功", tone: "success" },
  FAILED: { label: "失败", tone: "danger" },
  CANCELLED: { label: "已取消", tone: "neutral" }
};

export default {
  name: "TaskProgress",
  props: {
    expectedType: { type: String, required: true }
  },
  data() {
    return { store: state };
  },
  computed: {
    /** 返回当前任务详情。 */
    detail() {
      return this.store.activeTask;
    },
    /** 仅在当前任务属于本页面时显示进度。 */
    visible() {
      return this.detail?.task?.taskType === this.expectedType;
    },
    /** 按步骤号和执行器编号稳定排序。 */
    steps() {
      if (!this.detail) return [];
      return [...this.detail.steps].sort((left, right) =>
        left.stepNo - right.stepNo || String(left.executorId).localeCompare(String(right.executorId))
      );
    },
    /** 已结束的节点步骤数量。 */
    completed() {
      return this.steps.filter(step => ["SUCCESS", "FAILED", "CANCELLED"].includes(step.stepStatus)).length;
    },
    /** 当前任务完成百分比。 */
    percent() {
      return this.steps.length ? Math.round(this.completed / this.steps.length * 100) : 0;
    },
    /** 当前任务状态对应的显示信息。 */
    taskMeta() {
      if (!this.detail) return TASK_STATUS.CREATED;
      return TASK_STATUS[this.detail.task.taskStatus] || { label: this.detail.task.taskStatus, tone: "neutral" };
    },
    /** 进度条成功或失败配色。 */
    progressClass() {
      return { success: this.taskMeta.tone === "success", failed: this.taskMeta.tone === "danger" };
    }
  },
  methods: {
    /** 返回原子操作的中文名称。 */
    operationLabel(value) {
      return OPERATION_LABELS[value] || value;
    },
    /** 返回步骤状态的中文名称和配色。 */
    stepMeta(value) {
      return STEP_STATUS[value] || { label: value, tone: "neutral" };
    },
    /** 返回步骤所属服务器的可读名称。 */
    agentLabel(executorId) {
      const agent = this.store.agents.find(item => item.executorId === executorId);
      return agent ? `${agent.hostIp} · ${agent.executorName}` : executorId;
    },
    /** 格式化后端 LocalDateTime。 */
    formatDateTime(value) {
      return value ? String(value).replace("T", " ").slice(0, 16) : "-";
    }
  }
};
</script>
