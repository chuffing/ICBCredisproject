package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.FaultRecoveryConstants;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.constant.VersionPackageConstants;
import com.danuvius.platform.common.constant.VersionUpgradeConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.model.vo.VersionUpgradeResponseVO;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.service.VersionUpgradeService;
import com.danuvius.platform.util.Sha256Util;
import com.danuvius.platform.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis版本升级服务实现。
 */
@Service
@RequiredArgsConstructor
public class VersionUpgradeServiceImpl implements VersionUpgradeService {

    private final RedisVersionPackageMapper versionPackageMapper;

    private final RedisInstanceService redisInstanceService;

    private final SentinelNodeInfoService sentinelNodeInfoService;

    private final ExecutorInfoService executorInfoService;

    private final OperationTaskService operationTaskService;

    private final OperationTaskDispatchExecutor operationTaskDispatchExecutor;

    /**
     * 创建包含部署、停服、恢复配置、切换版本、启服和验收的完整升级任务。
     */
    @Override
    @Transactional
    public VersionUpgradeResponseVO upgrade(VersionUpgradeRequestDTO request) {

        // 1. 查询并校验页面选择的Redis安装包。
        RedisVersionPackage versionPackage = loadVersionPackage(
                request.getPackageId()
        );

        // 2. 再次校验平台本地安装包，防止文件丢失或上传后被修改。
        validateStoredPackage(versionPackage);

        // 3. 查询并校验本次升级涉及的三台Redis、Sentinel和执行器。
        UpgradeContext context = loadUpgradeContext();

        // 4. 创建版本升级总任务，并保存安装包及环境快照。
        String requestSnapshot = buildRequestSnapshot(
                request,
                versionPackage,
                context
        );
        OperationTask task = operationTaskService.createTask(
                OperationTaskTypeEnum.VERSION_UPGRADE,
                request.getOperationReason(),
                request.getCreatedBy(),
                requestSnapshot
        );

        // 5. 一次性生成并保存八个阶段、三台服务器的全部执行步骤。
        List<OperationTaskStep> steps = buildUpgradeSteps(
                task.getTaskNo(),
                versionPackage,
                context
        );
        operationTaskService.saveSteps(steps);

        // 6. 事务提交后只下发第一阶段，后续阶段由结果处理服务逐步推进。
        registerStageDispatchAfterCommit(
                task.getTaskNo(),
                VersionUpgradeConstants.DEPLOY_VERSION_STEP_NO
        );

        // 7. 立即返回任务信息，不等待整条升级链路执行完成。
        return new VersionUpgradeResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                versionPackage.getRedisVersion(),
                VersionUpgradeConstants.TASK_CREATED_MESSAGE,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public VersionUpgradeResponseVO getUpgradeStatus(String taskNo) {

        OperationTask task = operationTaskService.getTaskByTaskNo(taskNo);
        if (task == null || !OperationTaskTypeEnum.VERSION_UPGRADE.name()
                .equals(task.getTaskType())) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到对应的版本升级任务"
            );
        }

        String targetVersion = null;
        try {
            Map<String, Object> snapshot = JSON.parseObject(
                    task.getRequestParams(),
                    Map.class
            );
            Object version = snapshot.get(
                    VersionUpgradeConstants.TARGET_VERSION_PARAM
            );
            targetVersion = version == null ? null : version.toString();

        } catch (Exception ignored) {
            // 历史任务快照无法解析时仍返回任务状态。
        }

        return new VersionUpgradeResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                targetVersion,
                buildTaskStatusMessage(task.getTaskStatus()),
                task.getFailureReason(),
                task.getTerminationReason(),
                task.getTerminatedBy(),
                task.getTerminatedTime()
        );
    }

    /**
     * 查询安装包记录，并确认当前允许用于升级。
     */
    private RedisVersionPackage loadVersionPackage(Long packageId) {

        RedisVersionPackage versionPackage = versionPackageMapper.selectById(
                packageId
        );
        if (versionPackage == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要升级的Redis安装包"
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
     * 校验平台正式存储中的安装包大小和摘要。
     */
    private void validateStoredPackage(RedisVersionPackage versionPackage) {

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
            if (versionPackage.getPackageSize() == null
                    || Files.size(packageFile)
                    != versionPackage.getPackageSize()) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis安装包文件大小与记录不一致"
                );
            }
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
     * 查询并组合三台服务器上的Redis、Sentinel和执行器。
     */
    private UpgradeContext loadUpgradeContext() {
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        if (redisInstances.size()
                != FaultRecoveryConstants.REQUIRED_REDIS_INSTANCE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "版本升级要求当前环境中正好存在3台已纳管Redis实例"
            );
        }

        List<SentinelNodeInfo> sentinelNodes = sentinelNodeInfoService.list(
                new LambdaQueryWrapper<SentinelNodeInfo>().eq(
                        SentinelNodeInfo::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        if (sentinelNodes.size()
                != FaultRecoveryConstants.REQUIRED_SENTINEL_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "版本升级要求当前环境中正好存在3台已纳管Sentinel节点"
            );
        }

        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>().eq(
                        ExecutorInfo::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );

        Map<String, SentinelNodeInfo> sentinelByHost = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : sentinelNodes) {
            String hostIp = StringUtils.trimToNull(sentinelNode.getNodeIp());
            if (hostIp == null
                    || sentinelByHost.put(hostIp, sentinelNode) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管Sentinel节点的服务器IP为空或重复"
                );
            }
        }

        Map<String, ExecutorInfo> executorByHost = new HashMap<>();
        for (ExecutorInfo executor : executors) {
            String hostIp = StringUtils.trimToNull(executor.getHostIp());
            if (hostIp == null
                    || executorByHost.put(hostIp, executor) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管执行器的服务器IP为空或重复"
                );
            }
        }

        List<UpgradeNode> upgradeNodes = new ArrayList<>();
        Set<String> redisHosts = new HashSet<>();
        for (RedisInstance redisInstance : redisInstances) {
            String hostIp = StringUtils.trimToNull(redisInstance.getHostIp());
            if (hostIp == null || !redisHosts.add(hostIp)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管Redis实例的服务器IP为空或重复"
                );
            }

            SentinelNodeInfo sentinelNode = sentinelByHost.remove(hostIp);
            ExecutorInfo executor = executorByHost.get(hostIp);
            if (sentinelNode == null || executor == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis服务器缺少同机的Sentinel或执行器：" + hostIp
                );
            }
            validateExecutor(executor, hostIp);
            validateConfigSha256(
                    redisInstance.getConfigSha256(),
                    "Redis",
                    hostIp
            );
            validateConfigSha256(
                    sentinelNode.getConfigSha256(),
                    "Sentinel",
                    hostIp
            );
            upgradeNodes.add(new UpgradeNode(
                    redisInstance,
                    sentinelNode,
                    executor
            ));
        }

        if (!sentinelByHost.isEmpty()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "存在未匹配Redis实例的已纳管Sentinel节点"
            );
        }
        return new UpgradeContext(upgradeNodes);
    }

    /**
     * 生成本次版本升级的全部24条执行步骤。
     */
    private List<OperationTaskStep> buildUpgradeSteps(
            String taskNo,
            RedisVersionPackage versionPackage,
            UpgradeContext context) {

        List<OperationTaskStep> steps = new ArrayList<>();

        // 1. 先在三台服务器部署目标版本，失败时不影响当前服务。
        Map<String, String> deployParams = new HashMap<>();
        deployParams.put(
                VersionUpgradeConstants.PACKAGE_ID_PARAM,
                String.valueOf(versionPackage.getId())
        );
        deployParams.put(
                VersionUpgradeConstants.TARGET_VERSION_PARAM,
                versionPackage.getRedisVersion()
        );
        deployParams.put(
                VersionUpgradeConstants.PACKAGE_SHA256_PARAM,
                versionPackage.getSha256()
        );

        addStageSteps(
                steps,
                taskNo,
                VersionUpgradeConstants.DEPLOY_VERSION_STEP_NO,
                context,
                ExecutorOperationTypeEnum.DEPLOY_VERSION,
                JSON.toJSONString(deployParams)
        );

        // 2. 停止三台Sentinel。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.STOP_SENTINEL_STEP_NO,
                context, ExecutorOperationTypeEnum.STOP_SENTINEL, null);

        // 3. 停止三台Redis。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.STOP_REDIS_STEP_NO,
                context, ExecutorOperationTypeEnum.STOP_REDIS, null);

        // 4. 按服务器恢复Redis和Sentinel配置文件。
        for (UpgradeNode node : context.upgradeNodes) {
            Map<String, String> restoreParams = new HashMap<>();
            restoreParams.put(
                    FaultRecoveryConstants.REDIS_CONFIG_SHA256_PARAM,
                    node.redisInstance.getConfigSha256()
            );
            restoreParams.put(
                    FaultRecoveryConstants.SENTINEL_CONFIG_SHA256_PARAM,
                    node.sentinelNode.getConfigSha256()
            );
            steps.add(createUpgradeStep(
                    taskNo,
                    VersionUpgradeConstants.RESTORE_CONFIG_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.RESTORE_CONFIG,
                    JSON.toJSONString(restoreParams)
            ));
        }

        // 5. 将三台服务器的current软链接切换到目标版本。
        Map<String, String> switchParams = new HashMap<>();
        switchParams.put(
                VersionUpgradeConstants.TARGET_VERSION_PARAM,
                versionPackage.getRedisVersion()
        );
        addStageSteps(
                steps,
                taskNo,
                VersionUpgradeConstants.SWITCH_VERSION_STEP_NO,
                context,
                ExecutorOperationTypeEnum.SWITCH_VERSION,
                JSON.toJSONString(switchParams)
        );

        // 6. 启动三台Redis。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.START_REDIS_STEP_NO,
                context, ExecutorOperationTypeEnum.START_REDIS, null);

        // 7. 启动三台Sentinel。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.START_SENTINEL_STEP_NO,
                context, ExecutorOperationTypeEnum.START_SENTINEL, null);

        // 8. 验证最终Redis和Sentinel拓扑。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.VERIFY_TOPOLOGY_STEP_NO,
                context,
                ExecutorOperationTypeEnum.VERIFY_TOPOLOGY,
                null);

        return steps;
    }

    private void addStageSteps(
            List<OperationTaskStep> steps,
            String taskNo,
            Integer stepNo,
            UpgradeContext context,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {
        for (UpgradeNode node : context.upgradeNodes) {
            steps.add(createUpgradeStep(
                    taskNo,
                    stepNo,
                    node,
                    operationType,
                    requestParams
            ));
        }
    }

    private OperationTaskStep createUpgradeStep(
            String taskNo,
            Integer stepNo,
            UpgradeNode node,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {
        LocalDateTime now = LocalDateTime.now();
        OperationTaskStep step = new OperationTaskStep();
        step.setTaskNo(taskNo);
        step.setStepNo(stepNo);
        step.setExecutorId(node.executorInfo.getId());
        step.setOperationType(operationType.name());
        step.setStepStatus(OperationTaskStepStatusEnum.CREATED.name());
        // 没有请求参数时保存空JSON对象，避免JSON字段写入null。
        step.setRequestParams(requestParams == null ? "{}" : requestParams);
        step.setResultJson("{}");
        step.setErrorCode("");
        step.setFailureReason("");
        step.setCreatedTime(now);
        step.setUpdatedTime(now);
        return step;
    }

    /**
     * 保存安装包和参与升级的节点信息，不在任务快照中暴露平台文件路径。
     */
    private String buildRequestSnapshot(
            VersionUpgradeRequestDTO request,
            RedisVersionPackage versionPackage,
            UpgradeContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(VersionUpgradeConstants.PACKAGE_ID_PARAM,
                versionPackage.getId());
        snapshot.put(VersionUpgradeConstants.TARGET_VERSION_PARAM,
                versionPackage.getRedisVersion());
        snapshot.put(VersionUpgradeConstants.PACKAGE_SHA256_PARAM,
                versionPackage.getSha256());
        snapshot.put("packageName", versionPackage.getPackageName());
        snapshot.put("operationReason", request.getOperationReason());
        snapshot.put("createdBy", request.getCreatedBy());

        List<Map<String, String>> nodes = new ArrayList<>();
        for (UpgradeNode node : context.upgradeNodes) {
            Map<String, String> nodeSnapshot = new LinkedHashMap<>();
            nodeSnapshot.put("hostIp", node.redisInstance.getHostIp());
            nodeSnapshot.put(VersionUpgradeConstants.REDIS_INSTANCE_ID_PARAM,
                    node.redisInstance.getInstanceId());
            nodeSnapshot.put("sentinelNodeId", node.sentinelNode.getId());
            nodeSnapshot.put("executorId", node.executorInfo.getId());
            nodes.add(nodeSnapshot);
        }
        snapshot.put(VersionUpgradeConstants.NODES_PARAM, nodes);
        return JSON.toJSONString(snapshot);
    }

    private void validateExecutor(ExecutorInfo executor, String hostIp) {
        if (StringUtils.trimToNull(executor.getId()) == null
                || !StatusEnum.ONLINE.getCode()
                .equals(executor.getExecutorStatus())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器上的执行器编号为空或当前不在线：" + hostIp
            );
        }
    }

    private void validateConfigSha256(
            String sha256,
            String configType,
            String hostIp) {
        String value = StringUtils.trimToNull(sha256);
        if (value == null || !ValidationConstants.SHA256_PATTERN
                .matcher(value).matches()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    hostIp + "服务器的" + configType
                            + "备份配置SHA256不正确"
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
                    "版本升级任务状态不正确"
            );
        }

        switch (status) {
            case CREATED:
                return VersionUpgradeConstants.TASK_CREATED_MESSAGE;
            case RUNNING:
                return VersionUpgradeConstants.TASK_RUNNING_MESSAGE;
            case RESULT_UNKNOWN:
                return VersionUpgradeConstants.TASK_RESULT_UNKNOWN_MESSAGE;
            case MANUALLY_TERMINATED:
                return VersionUpgradeConstants
                        .TASK_MANUALLY_TERMINATED_MESSAGE;
            case SUCCESS:
                return VersionUpgradeConstants.TASK_SUCCESS_MESSAGE;
            case FAILED:
                return VersionUpgradeConstants.TASK_FAILED_MESSAGE;
            default:
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "版本升级任务状态不正确"
                );
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class UpgradeContext {
        private final List<UpgradeNode> upgradeNodes;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class UpgradeNode {
        private final RedisInstance redisInstance;
        private final SentinelNodeInfo sentinelNode;
        private final ExecutorInfo executorInfo;
    }
}

