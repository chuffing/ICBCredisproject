<template>
  <div v-if="nodes.length === 3">
    <h3 v-if="includeSentinel" class="subsection-title">Redis 实例</h3>
    <div :class="includeSentinel ? 'status-grid' : 'node-grid'">
      <NodeCard v-for="agent in nodes" :key="agent.executorId" :agent="agent" />
    </div>
    <template v-if="includeSentinel">
      <h3 class="subsection-title">Sentinel 实例</h3>
      <div class="status-grid">
        <article v-for="agent in nodes" :key="`${agent.executorId}-sentinel`"
                 class="status-card" :class="`is-${sentinelHealth(agent).css}`">
          <div class="node-head">
            <span class="node-title">{{ agent.hostIp }}:{{ agent.redisInfo.sentinelPort || 26379 }}</span>
            <span class="badge badge-sentinel">SENTINEL</span>
          </div>
          <div class="node-meta">
            <div class="meta-row">
              <span>运行状态</span>
              <span class="status-value" :class="sentinelHealth(agent).css" :title="sentinelHealth(agent).detail">
                <i class="status-dot"></i>{{ sentinelHealth(agent).label }}
              </span>
            </div>
            <div class="meta-row"><span>监控 MASTER</span><strong>{{ sentinelMaster(agent) }}</strong></div>
            <div class="meta-row"><span>Sentinel 端口</span><strong>{{ agent.redisInfo.sentinelPort || 26379 }}</strong></div>
          </div>
        </article>
      </div>
    </template>
  </div>
  <div v-else class="empty-state">redis_info中还没有完整的三节点正式拓扑。</div>
</template>

<script>
import NodeCard, { metricHealth } from "./NodeCard.vue";
import { currentNodes, runtimeStatus } from "../store/appStore";

export default {
  name: "EnvironmentPanel",
  components: { NodeCard },
  props: { includeSentinel: { type: Boolean, default: false } },
  computed: {
    /** 返回redis_info中当前正式纳管的三台节点。 */
    nodes() { return currentNodes(); }
  },
  methods: {
    /** 返回执行器最近一次定时采集并写入数据库的Sentinel状态。 */
    sentinelHealth(agent) { return metricHealth(runtimeStatus(agent.executorId), "sentinel"); },
    /** 优先展示最近采集到的Sentinel监控MASTER。 */
    sentinelMaster(agent) {
      const metric = runtimeStatus(agent.executorId);
      if (metric?.sentinelMasterHost) {
        return `${metric.sentinelMasterHost}:${metric.sentinelMasterPort || 6379}`;
      }
      return agent.redisInfo.masterHost
        ? `${agent.redisInfo.masterHost}:${agent.redisInfo.masterPort || 6379}`
        : "未知";
    }
  }
};
</script>
