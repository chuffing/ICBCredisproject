import Vue from "vue";
import * as api from "../services/api";

const STORAGE_KEYS = {
  topology: "redis-ops-current-topology",
  activeTask: "redis-ops-active-task",
  pendingTopology: "redis-ops-pending-topology"
};

export const state = Vue.observable({
  agents: [],
  packages: [],
  runtimeStatuses: [],
  topology: null,
  activeTask: null,
  activeTaskNo: localStorage.getItem(STORAGE_KEYS.activeTask),
  loading: false,
  initialized: false,
  toasts: []
});

let pollingTimer = null;
let environmentTimer = null;
let toastSequence = 0;

/** 安全读取本地 JSON，数据损坏时返回 null。 */
function readJson(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || "null");
  } catch (error) {
    return null;
  }
}

/** 按执行器名称排序，保证三个节点的展示顺序稳定。 */
export function sortAgents(agents) {
  return [...agents].sort((left, right) =>
    String(left.executorName).localeCompare(String(right.executorName), "zh-CN")
  );
}

/** 保存浏览器当前使用的正式拓扑。 */
export function saveTopology(topology) {
  state.topology = topology;
  localStorage.setItem(STORAGE_KEYS.topology, JSON.stringify(topology));
}

/** 优先恢复已保存拓扑；首次使用时选择最近注册的三台执行器。 */
function inferTopology() {
  const saved = readJson(STORAGE_KEYS.topology);
  const knownIds = new Set(state.agents.map(agent => agent.executorId));
  if (saved && Array.isArray(saved.nodeIds)
    && saved.nodeIds.length === 3
    && saved.nodeIds.every(id => knownIds.has(id))
    && saved.nodeIds.includes(saved.masterId)) {
    state.topology = saved;
    return;
  }

  const eligible = state.agents
    .filter(agent => agent.managementStatus === "ACTIVE")
    .sort((left, right) => new Date(left.registerTime || 0) - new Date(right.registerTime || 0))
    .slice(-3);
  const nodes = sortAgents(eligible);
  const newestPackage = state.packages.find(item => item.verifyStatus === "PASSED");
  if (nodes.length === 3) {
    saveTopology({
      nodeIds: nodes.map(node => node.executorId),
      masterId: nodes[0].executorId,
      version: newestPackage ? newestPackage.version : null,
      health: "unknown"
    });
  }
}

/** 初始化页面所需的执行器、版本包和未结束任务。 */
export async function initialize(force = false) {
  if (state.initialized && !force) return;
  state.loading = true;
  try {
    const [agents, packages, statuses] = await Promise.all([
      api.listAgents(), api.listVersionPackages(), api.listLatestMetrics()
    ]);
    state.agents = agents || [];
    state.packages = packages || [];
    state.runtimeStatuses = statuses || [];
    inferTopology();
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

/** 返回当前正式拓扑对应的三台执行器。 */
export function currentNodes() {
  if (!state.topology) return [];
  return state.topology.nodeIds
    .map(id => state.agents.find(agent => agent.executorId === id))
    .filter(Boolean);
}

/** 返回当前拓扑之外可以用于服务器切换的执行器。 */
export function targetCandidates() {
  const currentIds = new Set(state.topology ? state.topology.nodeIds : []);
  return sortAgents(state.agents.filter(agent =>
    agent.managementStatus === "ACTIVE" && !currentIds.has(agent.executorId)
  ));
}

/** 为三类运维任务生成共用的当前拓扑参数。 */
export function buildTopologyPayload(operationReason) {
  const nodes = currentNodes();
  if (nodes.length !== 3 || !state.topology?.masterId) {
    throw new Error("当前环境不是完整的三节点拓扑");
  }
  if (nodes.some(agent => agent.executorStatus !== "ONLINE")) {
    throw new Error("当前拓扑存在离线执行器，不能创建运维任务");
  }
  return {
    masterExecutorId: state.topology.masterId,
    redisPort: 6379,
    nodes: nodes.map(agent => ({ executorId: agent.executorId })),
    createdBy: "admin",
    operationReason: operationReason.trim()
  };
}

/** 保存新任务并启动任务结果轮询。 */
export function acceptCreatedTask(detail, pendingChange) {
  state.activeTask = detail;
  state.activeTaskNo = detail.task.taskNo;
  localStorage.setItem(STORAGE_KEYS.activeTask, state.activeTaskNo);
  localStorage.setItem(STORAGE_KEYS.pendingTopology, JSON.stringify({
    taskNo: state.activeTaskNo,
    ...pendingChange
  }));
  notify(`任务 ${state.activeTaskNo} 已创建`, "success");
  startTaskPolling();
}

/** 查询当前任务的最新步骤结果。 */
export async function refreshTask(showError = true) {
  if (!state.activeTaskNo) return;
  try {
    state.activeTask = await api.getOperationTask(state.activeTaskNo);
    applyFinishedTask();
  } catch (error) {
    if (showError) notify(error.message, "error");
  }
}

/** 任务成功后更新当前版本或正式服务器拓扑。 */
function applyFinishedTask() {
  if (!state.activeTask) return;
  const status = state.activeTask.task.taskStatus;
  if (!["SUCCESS", "FAILED", "ROLLED_BACK"].includes(status)) return;
  const pending = readJson(STORAGE_KEYS.pendingTopology);
  if (status === "SUCCESS" && pending?.taskNo === state.activeTask.task.taskNo) {
    if (pending.type === "switch") {
      saveTopology({
        nodeIds: pending.nodeIds,
        masterId: pending.masterId,
        version: pending.version,
        health: "healthy"
      });
    } else if (state.topology) {
      saveTopology({
        ...state.topology,
        version: pending.type === "upgrade" ? pending.version : state.topology.version,
        health: "healthy"
      });
    }
  }
  localStorage.removeItem(STORAGE_KEYS.pendingTopology);
  stopTaskPolling();
}

/** 每 1.5 秒刷新一次任务状态。 */
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

/** 删除已经超时的反馈消息。 */
export function removeToast(id) {
  const index = state.toasts.findIndex(item => item.id === id);
  if (index >= 0) state.toasts.splice(index, 1);
}

/** 返回指定执行器最近一次上报的Redis和Sentinel状态。 */
export function runtimeStatus(executorId) {
  return state.runtimeStatuses.find(item => item.executorId === executorId) || null;
}

/** 刷新执行器及其最新运行状态。 */
export async function refreshEnvironment() {
  const [agents, statuses] = await Promise.all([api.listAgents(), api.listLatestMetrics()]);
  state.agents = agents || [];
  state.runtimeStatuses = statuses || [];
}

/** 每15秒读取平台已经收到的最新状态，使页面自动反映执行器后续上报。 */
function startEnvironmentPolling() {
  if (environmentTimer) window.clearInterval(environmentTimer);
  environmentTimer = window.setInterval(() => {
    refreshEnvironment().catch(error => notify(error.message, "error"));
  }, 15000);
}