import Vue from "vue";
import App from "./App.vue";
import router from "./router";
import "./assets/app.css";

Vue.config.productionTip = false;

/** 创建 Vue 2 根实例并挂载到页面。 */
new Vue({
  router,
  render: createElement => createElement(App)
}).$mount("#app");
