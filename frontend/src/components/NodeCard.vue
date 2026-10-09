<template>
  <article class="node-card" :class="cardClasses">
    <div class="node-head">
      <span class="node-title">{{ agent.hostIp }}:6379</span>
      <span class="badge" :class="role === 'MASTER' ? 'badge-master' : 'badge-replica'">{{ role }}</span>
    </div>
    <div class="node-meta">
      <div class="meta-row"><span>执行器</span><strong>{{ agent.executorName }}</strong></div>
      <div class="meta-row">
        <span>执行器状态</span>
        <span class="status-value" :class="agent.executorStatus === 'ONLINE' ? 'success' : 'failed'">
          <i class="status-dot"></i>{{ agent.executorStatus === "ONLINE" ? "在线" : "离线" }}
        </span>
      </div>
      <div class="meta-row">
        <span>Redis 状态</span>
        <span class="status-value" :class="health.css" :title="health.detail">
          <i class="status-dot"></i>{{ health.label }}
        </span>
      </div>
      <div v-if="role === 'MASTER'" class="meta-row"><span>Redis 版本</span><strong>{{ redisVersion }}</strong></div>
      <div v-else class="meta-row"><span>当前 MASTER</span><strong>{{ masterAddress }}</strong></div>
    </div>
  </article>
</template>

<script>
import { state, runtimeStatus } from "../store/appStore";

export default {
  name: "NodeCard",
  props: {
    agent: { type: Object, required: true },
    selected: { type: Boolean, default: false }
  },
  data() { return { store: state }; },
  computed: {
    /** 获取该执行器最近一次真实采集结果。 */
    metric() { return runtimeStatus(this.agent.executorId); },
    /** 优先使用实际采集角色，首次上报前回退到正式拓扑。 */
    role() {
      return this.metric?.redisRole
        || (this.agent.executorId === this.topology.masterId ? "MASTER" : "REPLICA");
    },
    /** 读取响应式拓扑对象。 */
    topology() { return this.store.topology || {}; },
    /** 返回实际Redis版本。 */
    redisVersion() { return this.metric?.redisVersion || this.topology.version || "尚未上报"; },
    /** 返回副本实际连接的MASTER地址。 */
    masterAddress() {
      if (this.metric?.redisMasterHost) {
        return `${this.metric.redisMasterHost}:${this.metric.redisMasterPort || 6379}`;
      }
      const master = this.store.agents.find(item => item.executorId === this.topology.masterId);
      return master ? `${master.hostIp}:6379` : "尚未上报";
    },
    /** 根据真实采集结果计算Redis健康状态。 */
    health() { return metricHealth(this.metric, "redis"); },
    /** 根据健康状态给节点卡片增加边框和背景色。 */
    cardClasses() {
      return { selected: this.selected, [`is-${this.health.css}`]: true };
    }
  }
};

/** 将最近一次指标转换成页面状态；10分钟未更新视为数据过期。 */
function metricHealth(metric, type) {
  if (!metric) return { label: "尚未上报", css: "neutral", detail: "平台还没有收到该执行器的状态数据" };
  const received = new Date(metric.receivedTime).getTime();
  if (!Number.isFinite(received) || Date.now() - received > 10 * 60 * 1000) {
    return { label: "数据过期", css: "neutral", detail: "最近一次状态上报已经超过10分钟" };
  }
  const value = type === "sentinel" ? metric.sentinelStatus : metric.redisStatus;
  const error = type === "sentinel" ? metric.sentinelErrorMessage : metric.redisErrorMessage;
  if (value === "UP") return { label: "正常", css: "success", detail: "" };
  if (value === "DOWN") return { label: "异常", css: "failed", detail: error || "健康检查失败" };
  return { label: "状态未知", css: "neutral", detail: error || "执行器未返回有效状态" };
}

export { metricHealth };
</script>