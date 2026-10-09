/** 发送请求并把平台统一响应转换成业务数据或异常。 */
async function request(url, options = {}) {
  const response = await fetch(url, options);
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw new Error(`接口返回格式错误（HTTP ${response.status}）`);
  }
  if (!response.ok || body.code !== "SUCCESS") {
    throw new Error(body.message || `请求失败（HTTP ${response.status}）`);
  }
  return body.data;
}

/** 查询已经注册到平台的全部执行器。 */
export function listAgents() { return request("/api/agent"); }

/** 查询每台执行器最近上报的Redis和Sentinel状态。 */
export function listLatestMetrics() { return request("/api/metric/latest"); }

/** 查询平台中已经校验过的Redis版本包。 */
export function listVersionPackages() { return request("/api/version-packages"); }

/** 上传Redis tar.gz版本包。 */
export function uploadVersionPackage(formData) {
  return request("/api/version-packages", { method: "POST", body: formData });
}

/** 创建版本升级业务任务。 */
export function createVersionUpgrade(payload) { return postJson("/api/version-upgrade-tasks", payload); }

/** 创建服务器切换业务任务。 */
export function createServerSwitch(payload) { return postJson("/api/server-switch-tasks", payload); }

/** 创建故障恢复业务任务。 */
export function createFaultRecovery(payload) { return postJson("/api/fault-recovery-tasks", payload); }

/** 根据任务编号查询主任务和全部节点步骤。 */
export function getOperationTask(taskNo) {
  return request(`/api/operation-tasks/${encodeURIComponent(taskNo)}`);
}

/** 用JSON请求体调用平台POST接口。 */
function postJson(url, payload) {
  return request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}