package com.redisops.platform.versionupgrade.service.impl;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.model.VersionUpgradeNodeRequest;
import com.redisops.platform.versionupgrade.model.VersionUpgradeTaskRequest;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import com.redisops.platform.versionupgrade.service.VersionUpgradeService;
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
 * 把一次三节点版本升级拆成执行器能够执行的本机原子操作。
 */
@Service
@RequiredArgsConstructor
public class VersionUpgradeServiceImpl implements VersionUpgradeService {

    private static final int REQUIRED_NODE_COUNT = 3;
    private static final int DEFAULT_REDIS_PORT = 6379;

    private final OperationTaskService operationTaskService;
    private final AgentRegistrationService agentRegistrationService;
    private final VersionPackageService versionPackageService;

    /**
     * 创建“部署包、停止拓扑、切换版本、按主从顺序启动”的七阶段计划。
     */
    @Override
    public OperationTaskDetail createAndDispatch(VersionUpgradeTaskRequest request) {
        validateBasicRequest(request);
        int redisPort = request.getRedisPort() == null
                ? DEFAULT_REDIS_PORT : request.getRedisPort();
        RedisVersionPackage versionPackage = versionPackageService
                .requireAvailable(request.getVersionPackageId());
        List<ExecutorInfo> nodes = validateAndReadExecutors(request.getNodes());
        ExecutorInfo master = findMaster(nodes, request.getMasterExecutorId().trim());

        CreateOperationTaskRequest taskRequest = new CreateOperationTaskRequest();
        taskRequest.setTaskType(BusinessTaskType.VERSION_UPGRADE.name());
        taskRequest.setCreatedBy(request.getCreatedBy().trim());
        taskRequest.setOperationReason(request.getOperationReason().trim());
        taskRequest.setRequestParams(buildAuditParams(nodes, master, redisPort, versionPackage));
        taskRequest.setSteps(buildUpgradeSteps(nodes, master, redisPort, versionPackage));
        return operationTaskService.createAndDispatch(taskRequest);
    }

    /** 校验任务级字段、三节点数量和 Redis 端口。 */
    private void validateBasicRequest(VersionUpgradeTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("版本升级请求不能为空");
        }
        if (request.getVersionPackageId() == null || request.getVersionPackageId() <= 0) {
            throw new IllegalArgumentException("versionPackageId 必须大于 0");
        }
        if (!StringUtils.hasText(request.getMasterExecutorId())) {
            throw new IllegalArgumentException("masterExecutorId 不能为空");
        }
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (request.getNodes() == null || request.getNodes().size() != REQUIRED_NODE_COUNT) {
            throw new IllegalArgumentException("版本升级必须且只能包含 3 台服务器");
        }
        if (request.getRedisPort() != null
                && (request.getRedisPort() < 1 || request.getRedisPort() > 65535)) {
            throw new IllegalArgumentException("redisPort 必须在 1 到 65535 之间");
        }
    }

    /** 校验三台执行器编号唯一且当前在线。 */
    private List<ExecutorInfo> validateAndReadExecutors(
            List<VersionUpgradeNodeRequest> requestedNodes) {
        Set<String> executorIds = new HashSet<>();
        List<ExecutorInfo> executors = new ArrayList<>();
        for (VersionUpgradeNodeRequest requestedNode : requestedNodes) {
            if (requestedNode == null || !StringUtils.hasText(requestedNode.getExecutorId())) {
                throw new IllegalArgumentException("每个节点的 executorId 都不能为空");
            }
            String executorId = requestedNode.getExecutorId().trim();
            if (!executorIds.add(executorId)) {
                throw new IllegalArgumentException("executorId 不能重复: " + executorId);
            }
            executors.add(agentRegistrationService.requireOnlineExecutor(executorId));
        }
        return executors;
    }

    /** 找到本次升级前后保持不变的主 Redis 节点。 */
    private ExecutorInfo findMaster(List<ExecutorInfo> nodes, String masterExecutorId) {
        for (ExecutorInfo node : nodes) {
            if (masterExecutorId.equals(node.getExecutorId())) {
                return node;
            }
        }
        throw new IllegalArgumentException("masterExecutorId 必须属于本次升级的 3 台服务器");
    }

    /** 生成任务主记录中的版本、拓扑和节点审计参数。 */
    private Map<String, Object> buildAuditParams(List<ExecutorInfo> nodes,
                                                  ExecutorInfo master,
                                                  int redisPort,
                                                  RedisVersionPackage versionPackage) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topologyType", "THREE_NODE_REDIS_SENTINEL");
        params.put("versionPackageId", versionPackage.getId());
        params.put("version", versionPackage.getDeclaredVersion());
        params.put("packageSha256", versionPackage.getPackageHash());
        params.put("packageName", versionPackage.getPackageName());
        params.put("masterExecutorId", master.getExecutorId());
        params.put("masterHost", master.getHostIp());
        params.put("redisPort", redisPort);

        List<Map<String, String>> nodeParams = new ArrayList<>();
        for (ExecutorInfo node : nodes) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("executorId", node.getExecutorId());
            item.put("hostIp", node.getHostIp());
            item.put("role", node.getExecutorId().equals(master.getExecutorId())
                    ? "MASTER" : "REPLICA");
            nodeParams.add(item);
        }
        params.put("nodes", nodeParams);
        return params;
    }

    /**
     * 生成18条正常升级步骤；同一个 stepNo 在三台服务器上并行执行。
     */
    private List<CreateOperationTaskStepRequest> buildUpgradeSteps(
            List<ExecutorInfo> nodes, ExecutorInfo master, int redisPort,
            RedisVersionPackage versionPackage) {
        List<CreateOperationTaskStepRequest> steps = new ArrayList<>();
        Map<String, String> versionParams = versionParams(versionPackage);

        // 第1组只把安装包部署到 releases 目录，不停止当前正在运行的Redis。
        for (ExecutorInfo node : nodes) {
            addStep(steps, 1, node.getExecutorId(),
                    AgentOperationType.DEPLOY_VERSION, versionParams);
        }
        // 第2、3组冻结Sentinel自动切换，然后停止整个Redis拓扑。
        for (ExecutorInfo node : nodes) {
            addStep(steps, 2, node.getExecutorId(),
                    AgentOperationType.STOP_SENTINEL, Collections.emptyMap());
        }
        for (ExecutorInfo node : nodes) {
            addStep(steps, 3, node.getExecutorId(),
                    AgentOperationType.STOP_REDIS, Collections.emptyMap());
        }
        // 第4组在三台机器都停止后切换current；Agent会再次校验包摘要和真实版本。
        for (ExecutorInfo node : nodes) {
            addStep(steps, 4, node.getExecutorId(),
                    AgentOperationType.SWITCH_VERSION, versionParams);
        }
        // 第5组先启动主节点并确认新版本Redis实际承担MASTER角色。
        addStep(steps, 5, master.getExecutorId(), AgentOperationType.START_REDIS,
                singleParam("expectedRole", "MASTER"));

        // 第6组启动两个从节点，并验证复制源仍然是指定主节点。
        for (ExecutorInfo node : nodes) {
            if (node.getExecutorId().equals(master.getExecutorId())) {
                continue;
            }
            Map<String, String> replicaParams = new LinkedHashMap<>();
            replicaParams.put("expectedRole", "REPLICA");
            replicaParams.put("expectedMasterHost", master.getHostIp());
            replicaParams.put("expectedMasterPort", String.valueOf(redisPort));
            addStep(steps, 6, node.getExecutorId(),
                    AgentOperationType.START_REDIS, replicaParams);
        }
        // 第7组重新启用三台Sentinel，并确认它们仍监控升级后的主节点。
        Map<String, String> sentinelParams = new LinkedHashMap<>();
        sentinelParams.put("expectedMasterHost", master.getHostIp());
        sentinelParams.put("expectedMasterPort", String.valueOf(redisPort));
        for (ExecutorInfo node : nodes) {
            addStep(steps, 7, node.getExecutorId(),
                    AgentOperationType.START_SENTINEL, sentinelParams);
        }
        return steps;
    }

    /** 构造部署和切换版本时必须一致的版本号与摘要参数。 */
    private Map<String, String> versionParams(RedisVersionPackage versionPackage) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("version", versionPackage.getDeclaredVersion());
        params.put("packageSha256", versionPackage.getPackageHash());
        return params;
    }

    /** 创建只有一个键值的操作参数。 */
    private Map<String, String> singleParam(String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        return params;
    }

    /** 向通用执行计划加入一台执行器上的一个原子操作。 */
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
}
