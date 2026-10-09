package com.redisops.platform.serverswitch.service.impl;

import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import com.redisops.platform.redisinfo.service.RedisInfoService;
import com.redisops.platform.serverswitch.model.ServerSwitchNodeRequest;
import com.redisops.platform.serverswitch.model.ServerSwitchTaskRequest;
import com.redisops.platform.serverswitch.service.ServerSwitchService;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
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
 * 把一次三节点服务器切换拆成新服务器上的本机原子操作。
 */
@Service
public class ServerSwitchServiceImpl implements ServerSwitchService {

    private static final int REQUIRED_NODE_COUNT = 3;
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int DEFAULT_SENTINEL_PORT = 26379;

    private final OperationTaskService operationTaskService;
    private final AgentRegistrationService agentRegistrationService;
    private final VersionPackageService versionPackageService;
    private final ExecutorConfigSnapshotService configSnapshotService;
    private final RedisInfoService redisInfoService;

    /** 注入任务调度、执行器、版本包和平台配置快照服务。 */
    public ServerSwitchServiceImpl(OperationTaskService operationTaskService,
                                   AgentRegistrationService agentRegistrationService,
                                   VersionPackageService versionPackageService,
                                   ExecutorConfigSnapshotService configSnapshotService,
                                   RedisInfoService redisInfoService) {
        this.operationTaskService = operationTaskService;
        this.agentRegistrationService = agentRegistrationService;
        this.versionPackageService = versionPackageService;
        this.configSnapshotService = configSnapshotService;
        this.redisInfoService = redisInfoService;
    }

    /**
     * 校验三台新服务器，生成预检、部署和启动新拓扑的九阶段计划。
     */
    @Override
    public OperationTaskDetail createAndDispatch(ServerSwitchTaskRequest request) {
        validateBasicRequest(request);
        int redisPort = request.getRedisPort() == null
                ? DEFAULT_REDIS_PORT : request.getRedisPort();
        RedisVersionPackage versionPackage = versionPackageService
                .requireAvailable(request.getVersionPackageId());
        List<ValidatedNode> nodes = validateAndReadNodes(request.getNodes());
        List<ExecutorInfo> sourceNodes = readManagedSourceNodes(nodes);
        ValidatedNode master = findMaster(nodes, request.getMasterHostIp().trim());

        CreateOperationTaskRequest taskRequest = new CreateOperationTaskRequest();
        taskRequest.setTaskType(BusinessTaskType.SERVER_SWITCH.name());
        taskRequest.setCreatedBy(request.getCreatedBy().trim());
        taskRequest.setOperationReason(request.getOperationReason().trim());
        taskRequest.setRequestParams(buildAuditParams(
                nodes, sourceNodes, master, redisPort, versionPackage));
        taskRequest.setSteps(buildSwitchSteps(
                nodes, sourceNodes, master, redisPort, versionPackage));
        return operationTaskService.createAndDispatch(taskRequest);
    }

    /** 校验任务必填字段、节点数量和 Redis 端口。 */
    private void validateBasicRequest(ServerSwitchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("服务器切换请求不能为空");
        }
        if (request.getVersionPackageId() == null || request.getVersionPackageId() <= 0) {
            throw new IllegalArgumentException("versionPackageId 必须大于 0");
        }
        if (!StringUtils.hasText(request.getMasterHostIp())) {
            throw new IllegalArgumentException("masterHostIp 不能为空");
        }
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (request.getNodes() == null || request.getNodes().size() != REQUIRED_NODE_COUNT) {
            throw new IllegalArgumentException("服务器切换必须且只能包含 3 台新服务器");
        }
        if (request.getRedisPort() != null
                && (request.getRedisPort() < 1 || request.getRedisPort() > 65535)) {
            throw new IllegalArgumentException("redisPort 必须在 1 到 65535 之间");
        }
    }

    /** 根据用户输入的IP校验目标执行器唯一且在线，并读取平台保存的可信配置摘要。 */
    private List<ValidatedNode> validateAndReadNodes(
            List<ServerSwitchNodeRequest> requestedNodes) {
        Set<String> hostIps = new HashSet<>();
        List<ValidatedNode> nodes = new ArrayList<>();
        for (ServerSwitchNodeRequest requestedNode : requestedNodes) {
            if (requestedNode == null || !StringUtils.hasText(requestedNode.getHostIp())) {
                throw new IllegalArgumentException("每台新服务器的hostIp都不能为空");
            }
            String hostIp = requestedNode.getHostIp().trim();
            if (!hostIps.add(hostIp)) {
                throw new IllegalArgumentException("服务器IP不能重复: " + hostIp);
            }
            ExecutorInfo executor = agentRegistrationService.requireOnlineExecutorByHostIp(hostIp);
            ExecutorConfigSnapshot snapshot = configSnapshotService
                    .requireActiveSnapshot(executor.getExecutorId());
            nodes.add(new ValidatedNode(
                    executor,
                    snapshot.getRedisConfigSha256(),
                    snapshot.getSentinelConfigSha256()));
        }
        return nodes;
    }

    /** 从redis_info读取旧正式拓扑，并校验旧执行器在线且不与目标服务器重复。 */
    private List<ExecutorInfo> readManagedSourceNodes(List<ValidatedNode> targetNodes) {
        Set<String> targetIds = new HashSet<>();
        for (ValidatedNode targetNode : targetNodes) {
            targetIds.add(targetNode.executor.getExecutorId());
        }

        List<ExecutorInfo> result = new ArrayList<>();
        for (RedisInfo sourceNode : redisInfoService.requireManagedTopology()) {
            String executorId = sourceNode.getExecutorId();
            if (targetIds.contains(executorId)) {
                throw new IllegalArgumentException("新旧拓扑不能包含同一执行器: " + executorId);
            }
            result.add(agentRegistrationService.requireOnlineExecutor(executorId));
        }
        return result;
    }

    /** 根据用户输入的主节点IP找到新拓扑中唯一主节点。 */
    private ValidatedNode findMaster(List<ValidatedNode> nodes, String masterHostIp) {
        for (ValidatedNode node : nodes) {
            if (masterHostIp.equals(node.executor.getHostIp())) {
                return node;
            }
        }
        throw new IllegalArgumentException("masterHostIp必须属于本次切换的3台新服务器");
    }

    /** 保存版本、目标拓扑和配置摘要，供审计及失败补偿使用。 */
    private Map<String, Object> buildAuditParams(List<ValidatedNode> nodes,
                                                  List<ExecutorInfo> sourceNodes,
                                                  ValidatedNode master,
                                                  int redisPort,
                                                  RedisVersionPackage versionPackage) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topologyType", "THREE_NODE_REDIS_SENTINEL");
        params.put("versionPackageId", versionPackage.getId());
        params.put("version", versionPackage.getDeclaredVersion());
        params.put("packageSha256", versionPackage.getPackageHash());
        params.put("packageName", versionPackage.getPackageName());
        params.put("masterExecutorId", master.executor.getExecutorId());
        params.put("masterHost", master.executor.getHostIp());
        params.put("redisPort", redisPort);
        params.put("sentinelPort", DEFAULT_SENTINEL_PORT);

        List<Map<String, String>> nodeParams = new ArrayList<>();
        for (ValidatedNode node : nodes) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("executorId", node.executor.getExecutorId());
            item.put("hostIp", node.executor.getHostIp());
            item.put("role", node == master ? "MASTER" : "REPLICA");
            item.put("redisConfigSha256", node.redisConfigSha256);
            item.put("sentinelConfigSha256", node.sentinelConfigSha256);
            nodeParams.add(item);
        }
        params.put("nodes", nodeParams);

        List<Map<String, String>> sourceNodeParams = new ArrayList<>();
        for (ExecutorInfo sourceNode : sourceNodes) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("executorId", sourceNode.getExecutorId());
            item.put("hostIp", sourceNode.getHostIp());
            sourceNodeParams.add(item);
        }
        params.put("sourceNodes", sourceNodeParams);
        return params;
    }

    /**
     * 生成十阶段服务器切换计划；相同 stepNo 的三台节点可以并行执行。
     */
    private List<CreateOperationTaskStepRequest> buildSwitchSteps(
            List<ValidatedNode> nodes, List<ExecutorInfo> sourceNodes,
            ValidatedNode master, int redisPort,
            RedisVersionPackage versionPackage) {
        List<CreateOperationTaskStepRequest> steps = new ArrayList<>();
        Map<String, String> versionParams = versionParams(versionPackage);

        // 第1组只读检查新机目录、权限、脚本、systemd单元、端口和current链接。
        for (ValidatedNode node : nodes) {
            addStep(steps, 1, node.executor.getExecutorId(),
                    AgentOperationType.PRECHECK_HOST, Collections.emptyMap());
        }
        // 第2组把安装包部署到每台新机的releases目录，不启动任何服务。
        for (ValidatedNode node : nodes) {
            addStep(steps, 2, node.executor.getExecutorId(),
                    AgentOperationType.DEPLOY_VERSION, versionParams);
        }
        // 第3、4组显式确保Sentinel和Redis停止，配置替换只能在停止状态进行。
        for (ValidatedNode node : nodes) {
            addStep(steps, 3, node.executor.getExecutorId(),
                    AgentOperationType.STOP_SENTINEL, Collections.emptyMap());
        }
        for (ValidatedNode node : nodes) {
            addStep(steps, 4, node.executor.getExecutorId(),
                    AgentOperationType.STOP_REDIS, Collections.emptyMap());
        }
        // 第5组把平台保存并重新校验的两份配置通过multipart下发给对应Agent。
        for (ValidatedNode node : nodes) {
            Map<String, String> configParams = new LinkedHashMap<>();
            configParams.put("redisConfigSha256", node.redisConfigSha256);
            configParams.put("sentinelConfigSha256", node.sentinelConfigSha256);
            addStep(steps, 5, node.executor.getExecutorId(),
                    AgentOperationType.DEPLOY_CONFIG, configParams);
        }
        // 第6组在服务仍停止时创建current链接；Agent会复核包摘要和实际版本。
        for (ValidatedNode node : nodes) {
            addStep(steps, 6, node.executor.getExecutorId(),
                    AgentOperationType.SWITCH_VERSION, versionParams);
        }
        // 第7组在启动任何新Redis前停止旧Sentinel，避免它把历史副本重新指向旧主库。
        for (ExecutorInfo sourceNode : sourceNodes) {
            addStep(steps, 7, sourceNode.getExecutorId(),
                    AgentOperationType.STOP_SENTINEL, Collections.emptyMap());
        }
        // 第8组启动新主节点，并连续确认它实际承担MASTER角色。
        addStep(steps, 8, master.executor.getExecutorId(),
                AgentOperationType.START_REDIS,
                singleParam("expectedRole", "MASTER"));

        // 第9组启动两个新副本，并确认复制源和链路都正确。
        for (ValidatedNode node : nodes) {
            if (node == master) {
                continue;
            }
            Map<String, String> replicaParams = new LinkedHashMap<>();
            replicaParams.put("expectedRole", "REPLICA");
            replicaParams.put("expectedMasterHost", master.executor.getHostIp());
            replicaParams.put("expectedMasterPort", String.valueOf(redisPort));
            addStep(steps, 9, node.executor.getExecutorId(),
                    AgentOperationType.START_REDIS, replicaParams);
        }
        // 第10组启动新Sentinel，并强制验收其最终监控地址必须指向新主库。
        Map<String, String> sentinelParams = new LinkedHashMap<>();
        sentinelParams.put("expectedMasterHost", master.executor.getHostIp());
        sentinelParams.put("expectedMasterPort", String.valueOf(redisPort));
        for (ValidatedNode node : nodes) {
            addStep(steps, 10, node.executor.getExecutorId(),
                    AgentOperationType.START_SENTINEL, sentinelParams);
        }
        return steps;
    }

    /** 构造版本部署和首次激活共同使用的版本参数。 */
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

    /** 向切换计划加入一台服务器上的一个原子操作。 */
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

    /** 已绑定执行器和平台配置摘要的新服务器。 */
    private static class ValidatedNode {
        private final ExecutorInfo executor;
        private final String redisConfigSha256;
        private final String sentinelConfigSha256;

        /** 保存构造任务步骤需要的执行器和配置摘要。 */
        private ValidatedNode(ExecutorInfo executor,
                              String redisConfigSha256,
                              String sentinelConfigSha256) {
            this.executor = executor;
            this.redisConfigSha256 = redisConfigSha256;
            this.sentinelConfigSha256 = sentinelConfigSha256;
        }
    }
}
