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
            <span class="node-title">{{ agent.hostIp }}:26379</span>
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
            <div class="meta-row"><span>Sentinel 端口</span><strong>26379</strong></div>
          </div>
        </article>
      </div>
    </template>
  </div>
  <div v-else class="empty-state">还没有识别到完整的三节点环境，请先确认三台执行器已经注册。</div>
</template>

<script>
import NodeCard, { metricHealth } from "./NodeCard.vue";
import { state, currentNodes, runtimeStatus } from "../store/appStore";

export default {
  name: "EnvironmentPanel",
  components: { NodeCard },
  props: { includeSentinel: { type: Boolean, default: false } },
  data() { return { store: state }; },
  computed: {
    /** 返回当前三台正式节点。 */
    nodes() { return currentNodes(); }
  },
  methods: {
    /** 返回指定节点最近一次Sentinel健康检查状态。 */
    sentinelHealth(agent) { return metricHealth(runtimeStatus(agent.executorId), "sentinel"); },
    /** 返回该Sentinel实际记录的MASTER地址。 */
    sentinelMaster(agent) {
      const metric = runtimeStatus(agent.executorId);
      if (metric?.sentinelMasterHost) {
        return `${metric.sentinelMasterHost}:${metric.sentinelMasterPort || 6379}`;
      }
      return "尚未上报";
    }
  }
};
</script>