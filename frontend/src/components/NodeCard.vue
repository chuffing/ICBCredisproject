<template>
  <article class="node-card" :class="cardClasses">
    <div class="node-head">
      <span class="node-title">{{ agent.hostIp }}:{{ redisInfo.redisPort || 6379 }}</span>
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
import { runtimeStatus } from "../store/appStore";

export default {
  name: "NodeCard",
  props: {
    agent: { type: Object, required: true },
    selected: { type: Boolean, default: false }
  },
  computed: {
    /** 当前卡片对应的redis_info正式纳管记录。 */
    redisInfo() { return this.agent.redisInfo || {}; },
    /** 执行器定时采集后写入数据库的最近一条运行状态。 */
    metric() { return runtimeStatus(this.agent.executorId); },
    /** 运行角色优先使用最新采集值，尚未采集时回退到正式纳管角色。 */
    role() { return this.metric?.redisRole || this.redisInfo.redisRole || "UNKNOWN"; },
    /** Redis版本优先展示最新采集值。 */
    redisVersion() { return this.metric?.redisVersion || this.redisInfo.redisVersion || "未知"; },
    /** 副本优先展示最新采集到的MASTER地址。 */
    masterAddress() {
      if (this.metric?.redisMasterHost) {
        return `${this.metric.redisMasterHost}:${this.metric.redisMasterPort || 6379}`;
      }
      return this.redisInfo.masterHost
        ? `${this.redisInfo.masterHost}:${this.redisInfo.masterPort || 6379}`
        : "未知";
    },
    /** Redis健康状态来自数据库中的最近一次定时采集记录。 */
    health() { return metricHealth(this.metric, "redis"); },
    /** 根据采集状态给节点卡片增加边框和背景色。 */
    cardClasses() {
      return { selected: this.selected, [`is-${this.health.css}`]: true };
    }
  }
};

/** 将数据库中的最近一次采集记录转换成页面状态；超过10分钟视为过期。 */
function metricHealth(metric, type) {
  if (!metric) return { label: "尚未上报", css: "neutral", detail: "数据库中还没有该执行器的采集记录" };
  const received = new Date(metric.receivedTime).getTime();
  if (!Number.isFinite(received) || Date.now() - received > 10 * 60 * 1000) {
    return { label: "数据过期", css: "neutral", detail: "数据库中的最近一次状态上报已经超过10分钟" };
  }
  const value = type === "sentinel" ? metric.sentinelStatus : metric.redisStatus;
  const error = type === "sentinel" ? metric.sentinelErrorMessage : metric.redisErrorMessage;
  if (value === "UP") return { label: "正常", css: "success", detail: "" };
  if (value === "DOWN") return { label: "异常", css: "failed", detail: error || "定时采集失败" };
  return { label: "状态未知", css: "neutral", detail: error || "执行器没有返回有效状态" };
}

export { metricHealth };
</script>
