<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">REDIS OPERATION</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button class="icon-button" type="button" title="刷新页面数据" @click="refresh">↻</button>
      </header>
      <main class="page-content">
        <section v-if="store.loading" class="panel">
          <div class="empty-state">正在读取平台数据…</div>
        </section>
        <router-view v-else />
      </main>
    </div>
    <div class="toast-region" aria-live="assertive">
      <div v-for="toast in store.toasts" :key="toast.id" class="toast" :class="toast.tone">
        {{ toast.message }}
      </div>
    </div>
  </div>
</template>

<script>
import AppSidebar from "./components/AppSidebar.vue";
import { state, initialize } from "./store/appStore";

export default {
  name: "App",
  components: { AppSidebar },
  data() {
    return { store: state };
  },
  computed: {
    /** 返回当前路由对应的页面标题。 */
    pageTitle() {
      return this.$route.meta.title || "运维管理";
    }
  },
  created() {
    initialize();
  },
  methods: {
    /** 重新从后端读取执行器、版本包和任务状态。 */
    refresh() {
      initialize(true);
    }
  }
};
</script>
