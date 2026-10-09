<template>
  <div class="page-stack">
    <section class="panel">
      <div class="section-heading"><div><h2>当前 Redis 环境</h2><p>切换任务成功前，当前正式纳管关系不会改变。</p></div></div>
      <EnvironmentPanel />
    </section>

    <section class="panel">
      <div class="section-heading"><div><h2>目标 Redis 环境</h2><p>选择三台已经注册执行器并保存过配置快照的新服务器。</p></div></div>
      <form @submit.prevent="submit">
        <label class="field target-version-field">
          <span>目标 Redis 版本 <em>*</em></span>
          <select v-model.number="versionPackageId" required>
            <option :value="null" disabled>请选择目标版本</option>
            <option v-for="item in availablePackages" :key="item.id" :value="item.id">Redis {{ item.version }} · {{ item.packageName }}</option>
          </select>
        </label>
        <div class="node-grid">
          <article v-for="(role, index) in roles" :key="index" class="node-card selected">
            <div class="node-head">
              <span class="node-title">目标服务器 {{ index + 1 }}</span>
              <span class="badge" :class="role === 'MASTER' ? 'badge-master' : 'badge-replica'">{{ role }}</span>
            </div>
            <select v-model="targetIds[index]" class="node-select" required>
              <option value="" disabled>请选择目标服务器</option>
              <option v-for="agent in candidates" :key="agent.executorId" :value="agent.executorId">
                {{ agent.hostIp }} · {{ agent.executorName }} · {{ agent.executorStatus === "ONLINE" ? "在线" : "离线" }}
              </option>
            </select>
            <div class="node-meta target-node-meta">
              <div class="meta-row"><span>Redis 端口</span><strong>6379</strong></div>
              <div class="meta-row"><span>Redis 配置</span><strong>使用服务器备份</strong></div>
              <div class="meta-row"><span>Sentinel 配置</span><strong>使用服务器备份</strong></div>
            </div>
          </article>
        </div>
        <div class="form-divider"></div>
        <label class="field"><span>操作原因 <em>*</em></span><textarea v-model.trim="operationReason" placeholder="请输入本次服务器切换原因" required></textarea></label>
        <div class="form-actions"><button class="button button-primary" type="submit" :disabled="submitting || current.length !== 3 || candidates.length < 3">{{ submitting ? "正在创建…" : "确认切换" }}</button></div>
      </form>
    </section>

    <TaskProgress expected-type="SERVER_SWITCH" />
  </div>
</template>

<script>
import EnvironmentPanel from "../components/EnvironmentPanel.vue";
import TaskProgress from "../components/TaskProgress.vue";
import * as api from "../services/api";
import { state, currentNodes, targetCandidates, acceptCreatedTask, notify } from "../store/appStore";

export default {
  name: "ServerSwitchView",
  components: { EnvironmentPanel, TaskProgress },
  data() {
    return {
      store: state,
      roles: ["MASTER", "REPLICA", "REPLICA"],
      targetIds: ["", "", ""],
      versionPackageId: null,
      operationReason: "",
      submitting: false
    };
  },
  computed: {
    /** 当前旧拓扑节点。 */
    current() {
      return currentNodes();
    },
    /** 当前拓扑之外的可选目标服务器。 */
    candidates() {
      return targetCandidates();
    },
    /** 只允许选择平台校验通过的版本包。 */
    availablePackages() {
      return this.store.packages.filter(item => item.verifyStatus === "PASSED");
    }
  },
  watch: {
    /** 执行器列表加载完成后默认填入前三台候选服务器。 */
    candidates: {
      immediate: true,
      handler(value) {
        if (this.targetIds.every(id => !id) && value.length >= 3) {
          this.targetIds = value.slice(0, 3).map(agent => agent.executorId);
        }
      }
    }
  },
  methods: {
    /** 校验目标节点并创建服务器切换任务。 */
    async submit() {
      this.submitting = true;
      try {
        if (new Set(this.targetIds).size !== 3) throw new Error("三台目标服务器不能重复");
        const targets = this.targetIds.map(id => this.store.agents.find(agent => agent.executorId === id));
        if (targets.some(agent => !agent)) throw new Error("请选择三台有效的目标服务器");
        if (targets.some(agent => agent.executorStatus !== "ONLINE")) throw new Error("目标服务器存在离线执行器，不能创建切换任务");

        const targetPackage = this.store.packages.find(item => item.id === this.versionPackageId);
        const payload = {
          versionPackageId: this.versionPackageId,
          masterExecutorId: this.targetIds[0],
          redisPort: 6379,
          nodes: this.targetIds.map(executorId => ({ executorId })),
          sourceNodes: this.current.map(agent => ({ executorId: agent.executorId })),
          createdBy: "admin",
          operationReason: this.operationReason
        };
        const detail = await api.createServerSwitch(payload);
        acceptCreatedTask(detail, {
          type: "switch",
          nodeIds: [...this.targetIds],
          masterId: this.targetIds[0],
          version: targetPackage?.version || null
        });
      } catch (error) {
        notify(error.message, "error");
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>
