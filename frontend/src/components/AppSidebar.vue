<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark">R</span>
      <span class="brand-copy">
        <strong>Redis 运维平台</strong>
        <small>Operation Console</small>
      </span>
    </div>
    <nav class="nav-list" aria-label="运维功能导航">
      <router-link class="nav-item" to="/upgrade"><span class="nav-icon">↥</span><span>版本升级</span></router-link>
      <router-link class="nav-item" to="/switch"><span class="nav-icon">⇄</span><span>服务器切换</span></router-link>
      <router-link class="nav-item" to="/recovery"><span class="nav-icon">↻</span><span>故障恢复</span></router-link>
    </nav>
    <div class="sidebar-foot">
      <span class="pulse-dot"></span>
      <span>{{ onlineCount }} / {{ store.agents.length }} 台执行器在线</span>
    </div>
  </aside>
</template>

<script>
import { state } from "../store/appStore";

export default {
  name: "AppSidebar",
  data() {
    return { store: state };
  },
  computed: {
    /** 统计正在按 30 秒周期上报心跳的执行器数量。 */
    onlineCount() {
      return this.store.agents.filter(agent => agent.executorStatus === "ONLINE").length;
    }
  }
};
</script>
