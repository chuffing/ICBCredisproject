<template>
  <div class="page-stack">
    <section class="panel">
      <div class="section-heading"><div><h2>Redis 环境状态</h2><p>展示当前纳管节点、执行器心跳以及最近一次上报的 Redis 与 Sentinel 状态。</p></div></div>
      <EnvironmentPanel include-sentinel />
    </section>
    <section class="panel">
      <div class="section-heading"><div><h2>执行故障恢复</h2><p>平台将调度当前环境的三台执行器，使用各服务器的备份配置恢复拓扑。</p></div></div>
      <form @submit.prevent="submit">
        <label class="field">
          <span>操作原因 <em>*</em></span>
          <textarea v-model.trim="operationReason" placeholder="请输入本次故障恢复原因" required></textarea>
          <small>操作原因会随任务结果写入审计记录。</small>
        </label>
        <div class="form-actions"><button class="button button-primary" type="submit" :disabled="submitting || nodes.length !== 3">{{ submitting ? "正在创建…" : "故障恢复" }}</button></div>
      </form>
    </section>
    <TaskProgress expected-type="FAULT_RECOVERY" />
  </div>
</template>

<script>
import EnvironmentPanel from "../components/EnvironmentPanel.vue";
import TaskProgress from "../components/TaskProgress.vue";
import * as api from "../services/api";
import { currentNodes, buildTopologyPayload, acceptCreatedTask, notify } from "../store/appStore";

export default {
  name: "FaultRecoveryView",
  components: { EnvironmentPanel, TaskProgress },
  data() {
    return { operationReason: "", submitting: false };
  },
  computed: {
    /** 当前恢复涉及的三台执行器。 */
    nodes() {
      return currentNodes();
    }
  },
  methods: {
    /** 创建故障恢复任务。 */
    async submit() {
      this.submitting = true;
      try {
        const payload = buildTopologyPayload(this.operationReason);
        const detail = await api.createFaultRecovery(payload);
        acceptCreatedTask(detail, { type: "recovery" });
      } catch (error) {
        notify(error.message, "error");
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>
