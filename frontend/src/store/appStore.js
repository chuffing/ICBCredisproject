import Vue from "vue";
import * as api from "../services/api";

const ACTIVE_TASK_KEY = "redis-ops-active-task";

export const state = Vue.observable({
  agents: [],
  redisInstances: [],
  runtimeStatuses: [],
  packages: [],
  activeTask: null,
  activeTaskNo: localStorage.getItem(ACTIVE_TASK_KEY),
  loading: false,
  initialized: false,
  toasts: []
});

let pollingTimer = null;
let environmentTimer = null;
let toastSequence = 0;

/** 按执行器名称排序，保证节点展示顺序稳定。 */
export function sortAgents(agents) {
  return [...agents].sort((left, right) =>
    String(left.executorName).localeCompare(String(right.executorName), "zh-CN")
  );
}

/** 初始化页面需要的执行器、正式Redis实例和版本包。 */
export async function initialize(force = false) {
  if (state.initialized && !force) return;
  state.loading = true;
  try {
    const [agents, redisInstances, runtimeStatuses, packages] = await Promise.all([
      api.listAgents(), api.listManagedRedis(), api.listLatestMetrics(), api.listVersionPackages()
    ]);
    state.agents = agents || [];
    state.redisInstances = redisInstances || [];
    state.runtimeStatuses = runtimeStatuses || [];
    state.packages = packages || [];
    if (state.activeTaskNo) await refreshTask(false);
    state.initialized = true;
    startEnvironmentPolling();
  } catch (error) {
    notify(error.message, "error");
  } finally {
    state.loading = false;
  }
}

/** 重新读取版本包列表。 */
export async function refreshPackages() {
  state.packages = await api.listVersionPackages();
}

/** 返回redis_info中正式纳管节点及其执行器心跳信息。 */
export function currentNodes() {
  const roleOrder = { MASTER: 0, REPLICA: 1 };
  return state.redisInstances
    .filter(item => item.managementStatus === "MANAGED")
    .map(redisInfo => {
      const agent = state.agents.find(item => item.executorId === redisInfo.executorId);
      return agent ? { ...agent, redisInfo } : null;
    })
    .filter(Boolean)
    .sort((left, right) => {
      const roleCompare = (roleOrder[left.redisInfo.redisRole] ?? 9)
        - (roleOrder[right.redisInfo.redisRole] ?? 9);
      return roleCompare || String(left.hostIp).localeCompare(String(right.hostIp));
    });
}

/** 为故障恢复和版本升级生成当前正式拓扑参数。 */
export function buildTopologyPayload(operationReason) {
  const nodes = currentNodes();
  const master = nodes.find(agent => agent.redisInfo.redisRole === "MASTER");
  if (nodes.length !== 3 || !master) {
    throw new Error("redis_info中没有完整的三节点正式拓扑");
  }
  if (nodes.some(agent => agent.executorStatus !== "ONLINE")) {
    throw new Error("当前正式拓扑存在离线执行器，不能创建运维任务");
  }
  return {
    masterExecutorId: master.executorId,
    redisPort: master.redisInfo.redisPort || 6379,
    nodes: nodes.map(agent => ({ executorId: agent.executorId })),
    createdBy: "admin",
    operationReason: operationReason.trim()
  };
}

/** 保存新任务并启动任务结果轮询。正式拓扑由后端成功事务提交。 */
export function acceptCreatedTask(detail) {
  state.activeTask = detail;
  state.activeTaskNo = detail.task.taskNo;
  localStorage.setItem(ACTIVE_TASK_KEY, state.activeTaskNo);
  notify(`任务 ${state.activeTaskNo} 已创建`, "success");
  startTaskPolling();
}

/** 查询当前任务的最新步骤结果。 */
export async function refreshTask(showError = true) {
  if (!state.activeTaskNo) return;
  try {
    state.activeTask = await api.getOperationTask(state.activeTaskNo);
    await applyFinishedTask();
  } catch (error) {
    if (showError) notify(error.message, "error");
  }
}

/** 任务结束后重新读取数据库中的正式拓扑，不在浏览器中自行修改拓扑。 */
async function applyFinishedTask() {
  if (!state.activeTask) return;
  const status = state.activeTask.task.taskStatus;
  if (!["SUCCESS", "FAILED", "ROLLED_BACK"].includes(status)) return;
  await refreshEnvironment();
  stopTaskPolling();
}

/** 每1.5秒刷新一次正在运行的任务。 */
function startTaskPolling() {
  stopTaskPolling();
  pollingTimer = window.setInterval(() => refreshTask(false), 1500);
}

/** 停止任务轮询。 */
function stopTaskPolling() {
  if (pollingTimer) {
    window.clearInterval(pollingTimer);
    pollingTimer = null;
  }
}

/** 添加一条短暂的页面反馈消息。 */
export function notify(message, tone = "info") {
  const id = ++toastSequence;
  state.toasts.push({ id, message, tone });
  window.setTimeout(() => removeToast(id), 4200);
}

/** 删除已经超时的页面反馈消息。 */
export function removeToast(id) {
  const index = state.toasts.findIndex(item => item.id === id);
  if (index >= 0) state.toasts.splice(index, 1);
}

/** 返回执行器定时采集并由平台保存到数据库的最近一条状态。 */
export function runtimeStatus(executorId) {
  return state.runtimeStatuses.find(item => item.executorId === executorId) || null;
}

/** 刷新执行器心跳、正式实例和数据库中的最近一次采集状态。 */
export async function refreshEnvironment() {
  const [agents, redisInstances, runtimeStatuses] = await Promise.all([
    api.listAgents(), api.listManagedRedis(), api.listLatestMetrics()
  ]);
  state.agents = agents || [];
  state.redisInstances = redisInstances || [];
  state.runtimeStatuses = runtimeStatuses || [];
}

/** 页面每15秒重新读取平台数据库；页面本身不会直接连接Redis或Sentinel。 */
function startEnvironmentPolling() {
  if (environmentTimer) window.clearInterval(environmentTimer);
  environmentTimer = window.setInterval(() => {
    refreshEnvironment().catch(error => notify(error.message, "error"));
  }, 15000);
}
