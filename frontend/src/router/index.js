import Vue from "vue";
import VueRouter from "vue-router";
import VersionUpgradeView from "../views/VersionUpgradeView.vue";
import ServerSwitchView from "../views/ServerSwitchView.vue";
import FaultRecoveryView from "../views/FaultRecoveryView.vue";

Vue.use(VueRouter);

const router = new VueRouter({
  mode: "hash",
  linkExactActiveClass: "active",
  routes: [
    { path: "/", redirect: "/upgrade" },
    { path: "/upgrade", name: "upgrade", component: VersionUpgradeView, meta: { title: "版本升级" } },
    { path: "/switch", name: "switch", component: ServerSwitchView, meta: { title: "服务器切换" } },
    { path: "/recovery", name: "recovery", component: FaultRecoveryView, meta: { title: "故障恢复" } },
    { path: "*", redirect: "/upgrade" }
  ]
});

/** 路由变化后同步浏览器标题。 */
router.afterEach(route => {
  document.title = `${route.meta.title || "运维管理"} - Redis 运维平台`;
});

export default router;
