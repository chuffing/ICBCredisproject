package com.redisops.platform.versionupgrade.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.service.OperationTaskRollbackPlanner;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 根据版本升级失败阶段生成跨三台服务器的补偿步骤。
 */
@Service
public class VersionUpgradeRollbackPlanner implements OperationTaskRollbackPlanner {

    public static final int ROLLBACK_STEP_START = 1001;

    private final ObjectMapper objectMapper;

    /** 注入任务参数 JSON 解析器。 */
    public VersionUpgradeRollbackPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 只处理 VERSION_UPGRADE 业务任务。 */
    @Override
    public boolean supports(OperationTask task) {
        return task != null && BusinessTaskType.VERSION_UPGRADE.name()
                .equals(task.getTaskType());
    }

    /**
     * 根据失败阶段生成恢复旧版本拓扑的步骤。
     *
     * <p>部署包失败时线上服务未受影响，无需补偿；停止或切换开始后，
     * 则按现场状态恢复服务，并对已经成功切换的节点执行版本回滚。</p>
     */
    @Override
    public List<CreateOperationTaskStepRequest> buildRollbackSteps(
            OperationTask task, List<OperationTaskStep> currentSteps) {
        int failedStepNo = findFirstFailedForwardStep(currentSteps);
        if (failedStepNo <= 1) {
            return Collections.emptyList();
        }

        UpgradeContext context = readContext(task.getRequestParams());
        Set<String> switchedExecutors = findSuccessfullySwitchedExecutors(currentSteps);
        List<CreateOperationTaskStepRequest> result = new ArrayList<>();
        int rollbackStepNo = ROLLBACK_STEP_START;

        if (failedStepNo == 2) {
            // Redis尚未停止，只需把可能已经停止的Sentinel重新启动。
            addForAll(result, rollbackStepNo, context.nodes,
                    AgentOperationType.START_SENTINEL, Collections.emptyMap());
            return result;
        }

        if (failedStepNo >= 5) {
            // 新版本可能已经运行，先重新冻结Sentinel，再确保三台Redis全部停止。
            addForAll(result, rollbackStepNo++, context.nodes,
                    AgentOperationType.STOP_SENTINEL, Collections.emptyMap());
            addForAll(result, rollbackStepNo++, context.nodes,
                    AgentOperationType.STOP_REDIS, Collections.emptyMap());
        }

        if (failedStepNo >= 4 && !switchedExecutors.isEmpty()) {
            // 只回滚已经成功切换的节点；切换失败的Agent会自行恢复本机旧链接。
            for (NodeContext node : context.nodes) {
                if (switchedExecutors.contains(node.executorId)) {
                    addStep(result, rollbackStepNo, node.executorId,
                            AgentOperationType.ROLLBACK_VERSION, Collections.emptyMap());
                }
            }
            rollbackStepNo++;
        }

        // Redis已经停止或版本已经回滚后，按主、从、Sentinel顺序恢复旧拓扑。
        addStep(result, rollbackStepNo++, context.masterExecutorId,
                AgentOperationType.START_REDIS, singleParam("expectedRole", "MASTER"));
        for (NodeContext node : context.nodes) {
            if (node.executorId.equals(context.masterExecutorId)) {
                continue;
            }
            Map<String, String> replicaParams = new LinkedHashMap<>();
            replicaParams.put("expectedRole", "REPLICA");
            replicaParams.put("expectedMasterHost", context.masterHost);
            replicaParams.put("expectedMasterPort", String.valueOf(context.redisPort));
            addStep(result, rollbackStepNo, node.executorId,
                    AgentOperationType.START_REDIS, replicaParams);
        }
        rollbackStepNo++;
        addForAll(result, rollbackStepNo, context.nodes,
                AgentOperationType.START_SENTINEL, Collections.emptyMap());
        return result;
    }

    /** 找到正常升级步骤中最早的失败阶段。 */
    private int findFirstFailedForwardStep(List<OperationTaskStep> steps) {
        int failedStepNo = Integer.MAX_VALUE;
        for (OperationTaskStep step : steps) {
            if (step.getStepNo() != null
                    && step.getStepNo() < ROLLBACK_STEP_START
                    && "FAILED".equals(step.getStepStatus())) {
                failedStepNo = Math.min(failedStepNo, step.getStepNo());
            }
        }
        if (failedStepNo == Integer.MAX_VALUE) {
            throw new IllegalStateException("版本升级进入回滚状态但没有失败步骤");
        }
        return failedStepNo;
    }

    /** 找出已经成功完成 SWITCH_VERSION 的节点。 */
    private Set<String> findSuccessfullySwitchedExecutors(List<OperationTaskStep> steps) {
        Set<String> executorIds = new HashSet<>();
        for (OperationTaskStep step : steps) {
            if (AgentOperationType.SWITCH_VERSION.name().equals(step.getOperationType())
                    && "SUCCESS".equals(step.getStepStatus())) {
                executorIds.add(step.getExecutorId());
            }
        }
        return executorIds;
    }

    /** 从任务审计 JSON 中读取三节点拓扑信息。 */
    private UpgradeContext readContext(String requestParams) {
        try {
            Map<String, Object> values = objectMapper.readValue(
                    requestParams, new TypeReference<Map<String, Object>>() { });
            String masterExecutorId = requiredString(values.get("masterExecutorId"),
                    "任务缺少 masterExecutorId");
            String masterHost = requiredString(values.get("masterHost"),
                    "任务缺少 masterHost");
            int redisPort = Integer.parseInt(requiredString(values.get("redisPort"),
                    "任务缺少 redisPort"));
            Object nodeValue = values.get("nodes");
            List<NodeContext> nodes = objectMapper.convertValue(
                    nodeValue, new TypeReference<List<NodeContext>>() { });
            if (nodes == null || nodes.size() != 3) {
                throw new IllegalStateException("版本升级审计参数必须包含3台节点");
            }
            for (NodeContext node : nodes) {
                if (node == null || !StringUtils.hasText(node.executorId)) {
                    throw new IllegalStateException("版本升级审计参数包含无效节点");
                }
            }
            return new UpgradeContext(masterExecutorId, masterHost, redisPort, nodes);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("版本升级审计参数无法解析", e);
        }
    }

    /** 把 JSON 值转换为必填文本。 */
    private String requiredString(Object value, String message) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    /** 给拓扑中的每一台执行器添加同一种补偿操作。 */
    private void addForAll(List<CreateOperationTaskStepRequest> steps,
                           int stepNo,
                           List<NodeContext> nodes,
                           AgentOperationType operationType,
                           Map<String, String> params) {
        for (NodeContext node : nodes) {
            addStep(steps, stepNo, node.executorId, operationType, params);
        }
    }

    /** 添加一条平台回滚步骤。 */
    private void addStep(List<CreateOperationTaskStepRequest> steps,
                         int stepNo,
                         String executorId,
                         AgentOperationType operationType,
                         Map<String, String> params) {
        CreateOperationTaskStepRequest step = new CreateOperationTaskStepRequest();
        step.setStepNo(stepNo);
        step.setExecutorId(executorId);
        step.setOperationType(operationType.name());
        step.setParams(new LinkedHashMap<>(params));
        steps.add(step);
    }

    /** 创建只有一个键值的操作参数。 */
    private Map<String, String> singleParam(String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        return params;
    }

    /** 从任务 JSON 反序列化的一台节点。 */
    public static class NodeContext {
        private String executorId;
        private String hostIp;
        private String role;

        /** 获取执行器编号。 */
        public String getExecutorId() { return executorId; }

        /** 设置执行器编号。 */
        public void setExecutorId(String executorId) { this.executorId = executorId; }

        /** 获取审计记录中的服务器IP。 */
        public String getHostIp() { return hostIp; }

        /** 设置审计记录中的服务器IP。 */
        public void setHostIp(String hostIp) { this.hostIp = hostIp; }

        /** 获取审计记录中的Redis角色。 */
        public String getRole() { return role; }

        /** 设置审计记录中的Redis角色。 */
        public void setRole(String role) { this.role = role; }
    }

    /** 回滚编排需要的不可变拓扑信息。 */
    private static class UpgradeContext {
        private final String masterExecutorId;
        private final String masterHost;
        private final int redisPort;
        private final List<NodeContext> nodes;

        /** 保存主节点、端口和三台节点。 */
        private UpgradeContext(String masterExecutorId, String masterHost,
                               int redisPort, List<NodeContext> nodes) {
            this.masterExecutorId = masterExecutorId;
            this.masterHost = masterHost;
            this.redisPort = redisPort;
            this.nodes = nodes;
        }
    }
}
