package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.OperationTaskConstants;
import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.constant.VersionPackageConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.ServerSwitchRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.model.vo.ServerSwitchResponseVO;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.NodeConfigFileService;
import com.danuvius.platform.service.ServerSwitchService;
import com.danuvius.platform.util.Sha256Util;
import com.danuvius.platform.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis服务器切换服务实现。
 */
@Service
@RequiredArgsConstructor
public class ServerSwitchServiceImpl implements ServerSwitchService {

    private final RedisVersionPackageMapper versionPackageMapper;

    private final RedisInstanceService redisInstanceService;

    private final ExecutorInfoService executorInfoService;

    private final OperationTaskService operationTaskService;

    private final OperationTaskDispatchExecutor operationTaskDispatchExecutor;

    private final NodeConfigFileService configFileService;

    /**
     * 创建包含新机部署、拓扑切换和旧机停服的完整任务。
     */
    @Override
    @Transactional
    public ServerSwitchResponseVO switchServer(
            ServerSwitchRequestDTO request) {

        // ======== 1. 获取并校验目标版本安装包 ========
        RedisVersionPackage versionPackage = loadVersionPackage(
                request.getPackageId()
        );
        validateStoredPackage(versionPackage);

        // ======== 2. 查询本次切换需要停止的旧服务器和执行器 ========
        List<SourceNode> sourceNodes = loadSourceNodes();

        // ======== 3. 查询目标执行器并校验staging目录下的人工配置 ========
        List<TargetNode> targetNodes = loadTargetNodes(
                request,
                sourceNodes
        );

        // 校验六份配置文件中的主从关系与本次选择一致。
        configFileService.validateStagingConfigContent(
                request.getMasterHostIp(),
                request.getReplicaHostIps()
        );

        // ======== 4. 创建总任务并保存前端请求参数 ========
        OperationTask task = operationTaskService.createTask(
                OperationTaskTypeEnum.SERVER_SWITCH,
                request.getOperationReason(),
                request.getCreatedBy(),
                JSON.toJSONString(request)
        );

        // ======== 5. 一次性生成并保存完整切换步骤 ========
        List<OperationTaskStep> steps = buildSwitchSteps(
                task.getTaskNo(),
                versionPackage,
                sourceNodes,
                targetNodes
        );
        operationTaskService.saveSteps(steps);

        // ======== 6. 事务提交后开始下发第一阶段 ========
        registerStageDispatchAfterCommit(
                task.getTaskNo(),
                ServerSwitchConstants.PRECHECK_HOST_STEP_NO
        );

        // ======== 7. 立即返回任务信息 ========
        return new ServerSwitchResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                operationTaskService.getCurrentStepNo(task.getTaskNo()),
                versionPackage.getRedisVersion(),
                request.getMasterHostIp(),
                ServerSwitchConstants.TASK_CREATED_MESSAGE,
                null,
                null,
                null,
                null
        );
    }


    @Override
    public ServerSwitchResponseVO getSwitchStatus(String taskNo) {
        OperationTask task = operationTaskService.getTaskByTaskNo(taskNo);
        if (task == null || !OperationTaskTypeEnum.SERVER_SWITCH.name()
                .equals(task.getTaskType())) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到对应的服务器切换任务"
            );
        }

        String targetVersion = null;
        String masterHostIp = null;
        try {
            ServerSwitchRequestDTO request = JSON.parseObject(
                    task.getRequestParams(),
                    ServerSwitchRequestDTO.class
            );
            RedisVersionPackage versionPackage = request == null
                    ? null
                    : versionPackageMapper.selectById(request.getPackageId());
            targetVersion = versionPackage == null
                    ? null
                    : versionPackage.getRedisVersion();
            masterHostIp = request == null
                    ? null
                    : request.getMasterHostIp();
        } catch (Exception ignored) {
            // 历史任务参数无法解析时仍返回任务状态。
        }

        return new ServerSwitchResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                operationTaskService.getCurrentStepNo(task.getTaskNo()),
                targetVersion,
                masterHostIp,
                buildTaskStatusMessage(task.getTaskStatus()),
                task.getFailureReason(),
                task.getTerminationReason(),
                task.getTerminatedBy(),
                task.getTerminatedTime()
        );
    }

    /**
     * 查询安装包记录，并确认当前允许用于服务器切换。
     */
    private RedisVersionPackage loadVersionPackage(Long packageId) {
        RedisVersionPackage versionPackage = versionPackageMapper.selectById(
                packageId
        );
        if (versionPackage == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到目标Redis版本安装包"
            );
        }
        if (!VersionPackageConstants.DEFAULT_PACKAGE_STATUS.equals(
                versionPackage.getPackageStatus())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前Redis安装包不可用"
            );
        }
        if (StringUtils.trimToNull(versionPackage.getRedisVersion()) == null
                || StringUtils.trimToNull(versionPackage.getSha256()) == null
                || StringUtils.trimToNull(versionPackage.getStoragePath())
                == null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "Redis安装包记录不完整"
            );
        }

        return versionPackage;
    }

    /**
     * 再次校验平台正式存储中的安装包。
     */
    private void validateStoredPackage(
            RedisVersionPackage versionPackage) {
        try {
            Path packageFile = Paths.get(versionPackage.getStoragePath())
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(packageFile)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis安装包文件不存在"
                );
            }

            // 重新计算大小。
            if (versionPackage.getPackageSize() == null
                    || Files.size(packageFile)
                    != versionPackage.getPackageSize()) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis安装包文件大小与记录不一致"
                );
            }

            // 重新计算SHA256值。
            String actualSha256 = Sha256Util.calculate(packageFile);
            if (!versionPackage.getSha256().equalsIgnoreCase(actualSha256)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis安装包文件SHA-256与记录不一致"
                );
            }

        } catch (BusinessException exception) {
            throw exception;

        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "读取Redis安装包文件失败：" + exception.getMessage()
            );
        }
    }

    /**
     * 查询当前三台正式Redis服务器及对应执行器。
     */
    private List<SourceNode> loadSourceNodes() {

        // 1. 查询当前三台正式Redis，确定服务器切换后需要停止的旧服务器。
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        if (redisInstances.size()
                != OperationTaskConstants.CURRENT_TOPOLOGY_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换要求当前环境中正好存在3台已纳管Redis实例"
            );
        }

        List<String> sourceInstanceIds = new ArrayList<>();
        for (RedisInstance redisInstance : redisInstances) {
            sourceInstanceIds.add(redisInstance.getInstanceId());
        }

        // 2. 根据Redis实例编号查询绑定的执行器。
        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                StatusEnum.ACTIVE.getCode())
                        .in(ExecutorInfo::getInstanceId,
                                sourceInstanceIds)
        );
        if (executors.size() != redisInstances.size()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前Redis实例绑定的执行器记录不完整"
            );
        }

        Map<String, ExecutorInfo> executorByInstanceId = new HashMap<>();
        for (ExecutorInfo executor : executors) {
            executorByInstanceId.put(executor.getInstanceId(), executor);
        }

        // 3. 通过实例编号组合旧Redis和执行器。
        List<SourceNode> sourceNodes = new ArrayList<>();
        for (RedisInstance redisInstance : redisInstances) {
            String hostIp = redisInstance.getHostIp();
            ExecutorInfo executor = executorByInstanceId.get(
                    redisInstance.getInstanceId()
            );

            if (executor == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "当前Redis实例缺少已绑定执行器："
                                + redisInstance.getInstanceId()
                );
            }

            validateExecutor(executor, hostIp);

            sourceNodes.add(new SourceNode(
                    hostIp,
                    executor
            ));
        }

        return sourceNodes;
    }

    /**
     * 按主节点、两个从节点的业务角色读取目标执行器和配置摘要。
     */
    private List<TargetNode> loadTargetNodes(
            ServerSwitchRequestDTO request,
            List<SourceNode> sourceNodes) {

        Set<String> sourceHostIps = new HashSet<>();
        for (SourceNode sourceNode : sourceNodes) {
            sourceHostIps.add(sourceNode.hostIp);
        }

        // 校验用户输入的目标服务器ip是否与当前正式服务器ip重复。
        List<String> targetHostIps = new ArrayList<>();
        targetHostIps.add(request.getMasterHostIp());
        targetHostIps.addAll(request.getReplicaHostIps());

        for (String targetHostIp : targetHostIps) {
            if (sourceHostIps.contains(targetHostIp)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "目标服务器不能与当前正式服务器重复："
                                + targetHostIp
                );
            }
        }

        // 根据目标服务器IP查出数据库中对应的执行器。
        List<ExecutorInfo> targetExecutors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                StatusEnum.INACTIVE.getCode())
                        .in(ExecutorInfo::getHostIp, targetHostIps)
        );
        Map<String, ExecutorInfo> executorByHost = new HashMap<>();
        for (ExecutorInfo executor : targetExecutors) {
            String hostIp = executor.getHostIp();
            if (executorByHost.put(hostIp, executor) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一目标服务器存在多个未纳管执行器：" + hostIp
                );
            }
        }

        List<TargetNode> targetNodes = new ArrayList<>();
        for (int index = 0; index < targetHostIps.size(); index++) {
            String hostIp = targetHostIps.get(index);
            ExecutorInfo executor = executorByHost.get(hostIp);
            if (executor == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "目标服务器缺少已纳管执行器：" + hostIp
                );
            }
            validateExecutor(executor, hostIp);

            // 从staging目录读取人工准备的两份配置文件。
            File redisConfigFile = configFileService
                    .getStagingRedisConfigFile(hostIp);
            File sentinelConfigFile = configFileService
                    .getStagingSentinelConfigFile(hostIp);

            // 计算两份配置文件的SHA256值。
            String redisConfigSha256 = configFileService.calculateSha256(
                    redisConfigFile,
                    hostIp + "服务器的Redis"
            );
            String sentinelConfigSha256 = configFileService.calculateSha256(
                    sentinelConfigFile,
                    hostIp + "服务器的Sentinel"
            );

            targetNodes.add(new TargetNode(
                    executor,
                    index == 0, // 如果索引为0说明是主节点，置1
                    redisConfigSha256,
                    sentinelConfigSha256
            ));
        }

        return targetNodes;
    }

    /**
     * 生成服务器切换的全部执行步骤。
     */
    private List<OperationTaskStep> buildSwitchSteps(
            String taskNo,
            RedisVersionPackage versionPackage,
            List<SourceNode> sourceNodes,
            List<TargetNode> targetNodes) {
        List<OperationTaskStep> steps = new ArrayList<>();

        // 1. 检查三台目标服务器是否满足部署条件。
        addTargetStageSteps(steps, taskNo,
                ServerSwitchConstants.PRECHECK_HOST_STEP_NO,
                targetNodes, ExecutorOperationTypeEnum.PRECHECK_HOST, null);

        // 2. 在三台目标服务器部署目标Redis版本。
        Map<String, String> deployVersionParams = new HashMap<>();
        deployVersionParams.put(ServerSwitchConstants.PACKAGE_ID_PARAM,
                String.valueOf(versionPackage.getId()));
        deployVersionParams.put(ServerSwitchConstants.TARGET_VERSION_PARAM,
                versionPackage.getRedisVersion());
        deployVersionParams.put(ServerSwitchConstants.PACKAGE_SHA256_PARAM,
                versionPackage.getSha256());
        addTargetStageSteps(steps, taskNo,
                ServerSwitchConstants.DEPLOY_VERSION_STEP_NO,
                targetNodes, ExecutorOperationTypeEnum.DEPLOY_VERSION,
                JSON.toJSONString(deployVersionParams));

        // 3.、4. 确保目标服务器上的旧Sentinel和Redis已经停止。
        addTargetStageSteps(steps, taskNo,
                ServerSwitchConstants.STOP_TARGET_SENTINEL_STEP_NO,
                targetNodes, ExecutorOperationTypeEnum.STOP_SENTINEL, null);
        addTargetStageSteps(steps, taskNo,
                ServerSwitchConstants.STOP_TARGET_REDIS_STEP_NO,
                targetNodes, ExecutorOperationTypeEnum.STOP_REDIS, null);

        // 5. 按目标服务器分别部署Redis和Sentinel配置文件。
        for (TargetNode targetNode : targetNodes) {
            Map<String, String> configParams = new HashMap<>();
            configParams.put(
                    ServerSwitchConstants.REDIS_CONFIG_SHA256_PARAM,
                    targetNode.redisConfigSha256
            );
            configParams.put(
                    ServerSwitchConstants.SENTINEL_CONFIG_SHA256_PARAM,
                    targetNode.sentinelConfigSha256
            );
            steps.add(createStep(
                    taskNo,
                    ServerSwitchConstants.DEPLOY_CONFIG_STEP_NO,
                    targetNode.executorInfo,
                    ExecutorOperationTypeEnum.DEPLOY_CONFIG,
                    JSON.toJSONString(configParams)
            ));
        }

        // 6. 将目标服务器的current软链接切换到目标版本。
        Map<String, String> switchVersionParams = new HashMap<>();
        switchVersionParams.put(ServerSwitchConstants.TARGET_VERSION_PARAM,
                versionPackage.getRedisVersion());
        addTargetStageSteps(steps, taskNo,
                ServerSwitchConstants.SWITCH_VERSION_STEP_NO,
                targetNodes, ExecutorOperationTypeEnum.SWITCH_VERSION,
                JSON.toJSONString(switchVersionParams));

        // 7. 先启动目标主节点，避免启动顺序依赖输入列表顺序。
        for (TargetNode targetNode : targetNodes) {
            if (targetNode.master) {
                steps.add(createStep(
                        taskNo,
                        ServerSwitchConstants.START_MASTER_REDIS_STEP_NO,
                        targetNode.executorInfo,
                        ExecutorOperationTypeEnum.START_REDIS,
                        null
                ));
            }
        }

        // 8. 再并行启动两个目标从节点。
        for (TargetNode targetNode : targetNodes) {
            if (!targetNode.master) {
                steps.add(createStep(
                        taskNo,
                        ServerSwitchConstants.START_REPLICA_REDIS_STEP_NO,
                        targetNode.executorInfo,
                        ExecutorOperationTypeEnum.START_REDIS,
                        null
                ));
            }
        }

        // 9. 启动三台目标Sentinel。
        addTargetStageSteps(steps, taskNo,
                ServerSwitchConstants.START_TARGET_SENTINEL_STEP_NO,
                targetNodes, ExecutorOperationTypeEnum.START_SENTINEL, null);

        // 10.、11. 目标环境启动后，依次停止旧Sentinel和旧Redis。
        addSourceStageSteps(steps, taskNo,
                ServerSwitchConstants.STOP_SOURCE_SENTINEL_STEP_NO,
                sourceNodes, ExecutorOperationTypeEnum.STOP_SENTINEL);
        addSourceStageSteps(steps, taskNo,
                ServerSwitchConstants.STOP_SOURCE_REDIS_STEP_NO,
                sourceNodes, ExecutorOperationTypeEnum.STOP_REDIS);

        return steps;
    }

    private void addTargetStageSteps(
            List<OperationTaskStep> steps,
            String taskNo,
            Integer stepNo,
            List<TargetNode> targetNodes,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {
        for (TargetNode targetNode : targetNodes) {
            steps.add(createStep(
                    taskNo,
                    stepNo,
                    targetNode.executorInfo,
                    operationType,
                    requestParams
            ));
        }
    }

    private void addSourceStageSteps(
            List<OperationTaskStep> steps,
            String taskNo,
            Integer stepNo,
            List<SourceNode> sourceNodes,
            ExecutorOperationTypeEnum operationType) {
        for (SourceNode sourceNode : sourceNodes) {
            steps.add(createStep(
                    taskNo,
                    stepNo,
                    sourceNode.executorInfo,
                    operationType,
                    null
            ));
        }
    }

    private OperationTaskStep createStep(
            String taskNo,
            Integer stepNo,
            ExecutorInfo executor,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {
        LocalDateTime now = LocalDateTime.now();
        OperationTaskStep step = new OperationTaskStep();
        step.setTaskNo(taskNo);
        step.setStepNo(stepNo);
        step.setExecutorId(executor.getId());
        step.setOperationType(operationType.name());
        step.setStepStatus(OperationTaskStepStatusEnum.CREATED.name());
        // 没有请求参数时保存空JSON对象，避免JSON字段写入null。
        step.setRequestParams(requestParams == null ? "{}" : requestParams);
        step.setResultJson("{}");
        step.setErrorCode(null);
        step.setFailureReason("");
        step.setCreatedTime(now);
        step.setUpdatedTime(now);
        return step;
    }

    private void validateExecutor(ExecutorInfo executor, String hostIp) {
        if (StringUtils.trimToNull(executor.getId()) == null
                || StringUtils.trimToNull(executor.getExecutorPort()) == null
                || StringUtils.trimToNull(executor.getExecutorToken()) == null
                || !StatusEnum.ONLINE.getCode()
                .equals(executor.getExecutorStatus())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器上的执行器连接信息不完整或当前不在线："
                            + hostIp
            );
        }
    }

    private void registerStageDispatchAfterCommit(
            String taskNo,
            Integer stepNo) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        operationTaskDispatchExecutor.dispatchStage(
                                taskNo,
                                stepNo
                        );
                    }
                }
        );
    }

    private String buildTaskStatusMessage(String taskStatus) {
        OperationTaskStatusEnum status;
        try {
            status = OperationTaskStatusEnum.valueOf(taskStatus);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务状态不正确"
            );
        }

        switch (status) {
            case CREATED:
                return ServerSwitchConstants.TASK_CREATED_MESSAGE;
            case RUNNING:
                return ServerSwitchConstants.TASK_RUNNING_MESSAGE;
            case RESULT_UNKNOWN:
                return ServerSwitchConstants.TASK_RESULT_UNKNOWN_MESSAGE;
            case MANUALLY_TERMINATED:
                return ServerSwitchConstants
                        .TASK_MANUALLY_TERMINATED_MESSAGE;
            case SUCCESS:
                return ServerSwitchConstants.TASK_SUCCESS_MESSAGE;
            case FAILED:
                return ServerSwitchConstants.TASK_FAILED_MESSAGE;
            default:
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "服务器切换任务状态不正确"
                );
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class SourceNode {
        private final String hostIp;
        private final ExecutorInfo executorInfo;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class TargetNode {
        private final ExecutorInfo executorInfo;
        private final boolean master;
        private final String redisConfigSha256;
        private final String sentinelConfigSha256;
    }
}

