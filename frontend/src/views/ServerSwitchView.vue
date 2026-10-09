<template>
  <div class="page-stack">
    <section class="panel">
      <div class="section-heading"><div><h2>当前 Redis 环境</h2><p>来自redis_info的当前正式纳管关系。</p></div></div>
      <EnvironmentPanel />
    </section>

    <section class="panel">
      <div class="section-heading"><div><h2>目标 Redis 环境</h2><p>输入三台已经注册并启动执行器的新服务器IP。</p></div></div>
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
            <label class="field target-ip-field">
              <span>服务器IP <em>*</em></span>
              <input v-model.trim="targetIps[index]" type="text"
                     :placeholder="role === 'MASTER' ? '请输入MASTER服务器IP' : '请输入REPLICA服务器IP'"
                     required />
            </label>
            <div class="node-meta target-node-meta">
              <div class="meta-row"><span>Redis 端口</span><strong>6379</strong></div>
              <div class="meta-row"><span>Redis 配置</span><strong>使用服务器备份</strong></div>
              <div class="meta-row"><span>Sentinel 配置</span><strong>使用服务器备份</strong></div>
            </div>
          </article>
        </div>
        <div class="form-divider"></div>
        <label class="field"><span>操作原因 <em>*</em></span><textarea v-model.trim="operationReason" placeholder="请输入本次服务器切换原因" required></textarea></label>
        <div class="form-actions"><button class="button button-primary" type="submit" :disabled="submitting || current.length !== 3">{{ submitting ? "正在创建…" : "确认切换" }}</button></div>
      </form>
    </section>

    <TaskProgress expected-type="SERVER_SWITCH" />
  </div>
</template>

<script>
import EnvironmentPanel from "../components/EnvironmentPanel.vue";
import TaskProgress from "../components/TaskProgress.vue";
import * as api from "../services/api";
import { state, currentNodes, acceptCreatedTask, notify } from "../store/appStore";

export default {
  name: "ServerSwitchView",
  components: { EnvironmentPanel, TaskProgress },
  data() {
    return {
      store: state,
      roles: ["MASTER", "REPLICA", "REPLICA"],
      targetIps: ["", "", ""],
      versionPackageId: null,
      operationReason: "",
      submitting: false
    };
  },
  computed: {
    /** 当前旧拓扑由redis_info提供。 */
    current() {
      return currentNodes();
    },
    /** 只允许选择平台校验通过的版本包。 */
    availablePackages() {
      return this.store.packages.filter(item => item.verifyStatus === "PASSED");
    }
  },
  methods: {
    /** 校验用户输入的三台IP并创建服务器切换任务。 */
    async submit() {
      this.submitting = true;
      try {
        const targetIps = this.targetIps.map(value => value.trim());
        if (targetIps.some(value => !value)) throw new Error("三台目标服务器IP都不能为空");
        if (new Set(targetIps).size !== 3) throw new Error("三台目标服务器IP不能重复");

        const payload = {
          versionPackageId: this.versionPackageId,
          masterHostIp: targetIps[0],
          redisPort: 6379,
          nodes: targetIps.map(hostIp => ({ hostIp })),
          createdBy: "admin",
          operationReason: this.operationReason
        };
        const detail = await api.createServerSwitch(payload);
        acceptCreatedTask(detail);
      } catch (error) {
        notify(error.message, "error");
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>
