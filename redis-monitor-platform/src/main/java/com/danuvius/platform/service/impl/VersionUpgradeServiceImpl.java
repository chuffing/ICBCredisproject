package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.danuvius.platform.common.constant.FaultRecoveryConstants;
import com.danuvius.platform.common.constant.VersionPackageConstants;
import com.danuvius.platform.common.constant.VersionUpgradeConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.CurrentTopologyNode;
import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.vo.VersionUpgradeResponseVO;
import com.danuvius.platform.service.CurrentTopologyService;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.service.VersionUpgradeService;
import com.danuvius.platform.util.Sha256Util;
import com.danuvius.platform.util.StringUtils;
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
import java.util.List;
import java.util.Map;

/**
 * Redis版本升级服务实现。
 */
@Service
@RequiredArgsConstructor
public class VersionUpgradeServiceImpl implements VersionUpgradeService {

    private final RedisVersionPackageMapper versionPackageMapper;

    private final CurrentTopologyService currentTopologyService;

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
        List<CurrentTopologyNode> nodes =
                currentTopologyService.loadCurrentNodes();

        // 4. 创建版本升级总任务，并保存前端请求参数。
        OperationTask task = operationTaskService.createTask(
                OperationTaskTypeEnum.VERSION_UPGRADE,
                request.getOperationReason(),
                request.getCreatedBy(),
                JSON.toJSONString(request)
        );

        // 5. 一次性生成并保存八个阶段、三台服务器的全部执行步骤。
        List<OperationTaskStep> steps = buildUpgradeSteps(
                task.getTaskNo(),
                versionPackage,
                nodes
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
                operationTaskService.getCurrentStepNo(task.getTaskNo()),
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
            VersionUpgradeRequestDTO request = JSON.parseObject(
                    task.getRequestParams(),
                    VersionUpgradeRequestDTO.class
            );
            RedisVersionPackage versionPackage = request == null
                    ? null
                    : versionPackageMapper.selectById(request.getPackageId());
            targetVersion = versionPackage == null
                    ? null
                    : versionPackage.getRedisVersion();

        } catch (Exception ignored) {
            // 历史任务参数无法解析时仍返回任务状态。
        }

        return new VersionUpgradeResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                operationTaskService.getCurrentStepNo(task.getTaskNo()),
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
     * 生成本次版本升级的全部24条执行步骤。
     */
    private List<OperationTaskStep> buildUpgradeSteps(
            String taskNo,
            RedisVersionPackage versionPackage,
            List<CurrentTopologyNode> nodes) {

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
                nodes,
                ExecutorOperationTypeEnum.DEPLOY_VERSION,
                JSON.toJSONString(deployParams)
        );

        // 2. 停止三台Sentinel。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.STOP_SENTINEL_STEP_NO,
                nodes, ExecutorOperationTypeEnum.STOP_SENTINEL, null);

        // 3. 停止三台Redis。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.STOP_REDIS_STEP_NO,
                nodes, ExecutorOperationTypeEnum.STOP_REDIS, null);

        // 4. 按服务器恢复Redis和Sentinel配置文件。
        for (CurrentTopologyNode node : nodes) {
            Map<String, String> restoreParams = new HashMap<>();
            restoreParams.put(
                    FaultRecoveryConstants.REDIS_CONFIG_SHA256_PARAM,
                    node.getRedisInstance().getConfigSha256()
            );
            restoreParams.put(
                    FaultRecoveryConstants.SENTINEL_CONFIG_SHA256_PARAM,
                    node.getSentinelNode().getConfigSha256()
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
                nodes,
                ExecutorOperationTypeEnum.SWITCH_VERSION,
                JSON.toJSONString(switchParams)
        );

        // 6. 启动三台Redis。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.START_REDIS_STEP_NO,
                nodes, ExecutorOperationTypeEnum.START_REDIS, null);

        // 7. 启动三台Sentinel。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.START_SENTINEL_STEP_NO,
                nodes, ExecutorOperationTypeEnum.START_SENTINEL, null);

        // 8. 验证最终Redis和Sentinel拓扑。
        addStageSteps(steps, taskNo,
                VersionUpgradeConstants.VERIFY_TOPOLOGY_STEP_NO,
                nodes,
                ExecutorOperationTypeEnum.VERIFY_TOPOLOGY,
                null);

        return steps;
    }

    private void addStageSteps(
            List<OperationTaskStep> steps,
            String taskNo,
            Integer stepNo,
            List<CurrentTopologyNode> nodes,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {
        for (CurrentTopologyNode node : nodes) {
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
            CurrentTopologyNode node,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {
        LocalDateTime now = LocalDateTime.now();
        OperationTaskStep step = new OperationTaskStep();
        step.setTaskNo(taskNo);
        step.setStepNo(stepNo);
        step.setExecutorId(node.getExecutorInfo().getId());
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

}

