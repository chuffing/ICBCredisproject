package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.exception.AgentTaskDispatchUnknownException;
import com.danuvius.platform.common.exception.AgentTaskRejectedException;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.AgentTaskRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.OperationTaskDispatchService;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.service.NodeConfigFileService;
import com.danuvius.platform.util.StringUtils;
import com.danuvius.platform.util.http.AgentTaskClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.io.File;

/**
 * 运维任务阶段调度服务实现。
 */
@Service
public class OperationTaskDispatchServiceImpl
        implements OperationTaskDispatchService {

    private final OperationTaskService operationTaskService;

    private final ExecutorInfoService executorInfoService;

    private final AgentTaskClient agentTaskClient;

    private final RedisVersionPackageMapper versionPackageMapper;

    private final NodeConfigFileService configFileService;

    private final Executor operationStepExecutor;

    public OperationTaskDispatchServiceImpl(
            OperationTaskService operationTaskService,
            ExecutorInfoService executorInfoService,
            AgentTaskClient agentTaskClient,
            RedisVersionPackageMapper versionPackageMapper,
            NodeConfigFileService configFileService,
            @Qualifier("operationStepExecutor") Executor operationStepExecutor) {
        this.operationTaskService = operationTaskService;
        this.executorInfoService = executorInfoService;
        this.agentTaskClient = agentTaskClient;
        this.versionPackageMapper = versionPackageMapper;
        this.configFileService = configFileService;
        this.operationStepExecutor = operationStepExecutor;
    }

    /**
     * 下发运维任务的指定阶段。
     */
    @Override
    public void dispatchStage(String taskNo, Integer stepNo) {

        // ======== 1. 查询当前阶段步骤 ========

        List<OperationTaskStep> stageSteps =
                operationTaskService.getStageSteps(taskNo, stepNo);

        // ======== 2. 阶段开始前统一校验执行器 ========

        Map<String, ExecutorInfo> executorById = loadExecutors(stageSteps);
        Map<String, String> rejectedReasons =
                validateStageExecutors(stageSteps, executorById);

        // 任意执行器不满足条件时，整个阶段都不发送HTTP。
        if (!rejectedReasons.isEmpty()) {
            operationTaskService.rejectStageBeforeDispatch(
                    taskNo,
                    stepNo,
                    stageSteps,
                    rejectedReasons
            );
            return;
        }

        // ======== 3. 在一个事务中启动总任务和当前阶段 ========

        stageSteps = operationTaskService.startStageDispatch(
                taskNo,
                stepNo,
                stageSteps
        );

        // ======== 4. 并行向三台执行器发送HTTP请求 ========

        List<CompletableFuture<DispatchResult>> dispatchFutures =
                new ArrayList<>();

        for (OperationTaskStep step : stageSteps) {
            CompletableFuture<DispatchResult> future =
                    CompletableFuture.supplyAsync(
                            () -> dispatchStep(
                                    step,
                                    executorById.get(step.getExecutorId())
                            ),
                            operationStepExecutor
                    );
            dispatchFutures.add(future);
        }

        // 等待当前阶段的HTTP接收确认全部返回，再统一保存步骤状态。
        CompletableFuture.allOf(
                dispatchFutures.toArray(new CompletableFuture[0])
        ).join();

        // ======== 5. 保存本阶段全部HTTP下发结果并检查下发结果是否已经足以结束总任务。 ========

        saveDispatchResults(taskNo, stepNo, dispatchFutures);
    }

    /**
     * 查询当前阶段涉及的执行器，并按照执行器编号建立索引。
     */
    private Map<String, ExecutorInfo> loadExecutors(
            List<OperationTaskStep> stageSteps) {

        List<String> executorIds = new ArrayList<>();
        for (OperationTaskStep step : stageSteps) {
            executorIds.add(step.getExecutorId());
        }

        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>().in(
                        ExecutorInfo::getId,
                        executorIds
                )
        );

        Map<String, ExecutorInfo> executorById = new HashMap<>();
        for (ExecutorInfo executor : executors) {
            executorById.put(executor.getId(), executor);
        }
        return executorById;
    }

    /**
     * 在整个阶段开始之前，一次性校验全部执行器。
     */
    private Map<String, String> validateStageExecutors(
            List<OperationTaskStep> stageSteps,
            Map<String, ExecutorInfo> executorById) {

        Map<String, String> rejectedReasons = new LinkedHashMap<>();

        for (OperationTaskStep step : stageSteps) {

            ExecutorInfo executor = executorById.get(step.getExecutorId());

            if (executor == null) {
                rejectedReasons.put(
                        step.getExecutorId(),
                        "执行器不存在：" + step.getExecutorId()
                );
            } else if (!StatusEnum.ONLINE.getCode()
                    .equals(executor.getExecutorStatus())) {
                rejectedReasons.put(
                        step.getExecutorId(),
                        "执行器当前不在线：" + step.getExecutorId()
                );
            } else if (StringUtils.trimToNull(
                    executor.getHostIp()) == null
                    || StringUtils.trimToNull(
                    executor.getExecutorPort()) == null
                    || StringUtils.trimToNull(
                    executor.getExecutorToken()) == null) {
                rejectedReasons.put(
                        step.getExecutorId(),
                        "执行器连接信息不完整：" + step.getExecutorId()
                );
            }
        }

        return rejectedReasons;
    }

    /**
     * 向一台已经通过阶段校验的执行器发送任务。
     */
    private DispatchResult dispatchStep(
            OperationTaskStep step,
            ExecutorInfo executor) {

        try {
            AgentTaskRequestDTO request = new AgentTaskRequestDTO();
            request.setTaskNo(step.getTaskNo());
            request.setStepNo(step.getStepNo());
            request.setOperationType(step.getOperationType());

            // 数据库中存在请求参数时，将JSON还原为执行器请求参数。
            if (step.getRequestParams() != null
                    && !step.getRequestParams().trim().isEmpty()) {
                try {
                    request.setParams(JSON.parseObject(
                            step.getRequestParams(),
                            new TypeReference<Map<String, String>>() {
                            }
                    ));
                } catch (Exception e) {
                    throw new AgentTaskRejectedException(
                            "平台保存的任务参数无法解析"
                    );
                }
            }

            // 版本部署阶段需要同时上传源码包。
            if (ExecutorOperationTypeEnum.DEPLOY_VERSION.name()
                    .equals(step.getOperationType())) {

                File packageFile = loadVersionPackageFile(request);

                agentTaskClient.dispatchVersionTask(
                        executor,
                        request,
                        packageFile
                );

                // 配置部署阶段需要按目标服务器上传两份人工配置。
            } else if (ExecutorOperationTypeEnum.DEPLOY_CONFIG.name()
                    .equals(step.getOperationType())) {
                try {

                    File redisConfigFile = configFileService
                            .getStagingRedisConfigFile(
                                    executor.getHostIp());

                    File sentinelConfigFile = configFileService
                            .getStagingSentinelConfigFile(
                                    executor.getHostIp());

                    validateConfigSha256(
                            request,
                            redisConfigFile,
                            sentinelConfigFile
                    );

                    agentTaskClient.dispatchConfigTask(
                            executor,
                            request,
                            redisConfigFile,
                            sentinelConfigFile
                    );

                } catch (BusinessException exception) {
                    throw new AgentTaskRejectedException(
                            exception.getMessage()
                    );
                }
                // 其他的运维类型不需要传文件了，正常发。
            } else {
                agentTaskClient.dispatchTask(executor, request);
            }
            return DispatchResult.dispatched(step);

        } catch (AgentTaskRejectedException e) {
            return DispatchResult.failed(step, e.getMessage());

        } catch (AgentTaskDispatchUnknownException e) {
            return DispatchResult.resultUnknown(step, e.getMessage());

        } catch (Exception e) {
            // HTTP调用期间发生未分类异常时，无法确认执行器是否收到任务。
            return DispatchResult.resultUnknown(
                    step,
                    "任务下发过程异常：" + e.getMessage()
            );
        }
    }

    /**
     * 根据部署步骤中的安装包编号读取平台正式存储文件。
     */
    private File loadVersionPackageFile(AgentTaskRequestDTO request) {

        Map<String, String> params = request.getParams();
        String packageIdValue = params == null
                ? null : params.get("packageId");

        long packageId;

        try {
            packageId = Long.parseLong(packageIdValue);

        } catch (NumberFormatException | NullPointerException exception) {
            throw new AgentTaskRejectedException(
                    "版本部署步骤中的安装包编号不正确"
            );
        }

        RedisVersionPackage versionPackage = versionPackageMapper.selectById(
                packageId
        );
        if (versionPackage == null
                || versionPackage.getStoragePath() == null) {
            throw new AgentTaskRejectedException("版本部署安装包不存在");
        }

        File packageFile = new File(versionPackage.getStoragePath());
        if (!packageFile.isFile()) {
            throw new AgentTaskRejectedException("版本部署安装包文件不存在");
        }

        return packageFile;
    }

    /**
     * 下发前重新计算两份配置的摘要，防止任务创建后文件被修改。
     */
    private void validateConfigSha256(
            AgentTaskRequestDTO request,
            File redisConfigFile,
            File sentinelConfigFile) {
        Map<String, String> params = request.getParams();
        String expectedRedisSha256 = params == null ? null
                : params.get(ServerSwitchConstants
                .REDIS_CONFIG_SHA256_PARAM);
        String expectedSentinelSha256 = params == null ? null
                : params.get(ServerSwitchConstants
                .SENTINEL_CONFIG_SHA256_PARAM);

        String actualRedisSha256;
        String actualSentinelSha256;
        try {
            actualRedisSha256 = configFileService.calculateSha256(
                    redisConfigFile,
                    "Redis"
            );
            actualSentinelSha256 = configFileService.calculateSha256(
                    sentinelConfigFile,
                    "Sentinel"
            );
        } catch (BusinessException exception) {
            throw new AgentTaskRejectedException(exception.getMessage());
        }

        if (expectedRedisSha256 == null
                || !expectedRedisSha256.equalsIgnoreCase(
                actualRedisSha256)) {
            throw new AgentTaskRejectedException(
                    "Redis配置文件已在任务创建后发生变化"
            );
        }
        if (expectedSentinelSha256 == null
                || !expectedSentinelSha256.equalsIgnoreCase(
                actualSentinelSha256)) {
            throw new AgentTaskRejectedException(
                    "Sentinel配置文件已在任务创建后发生变化"
            );
        }
    }

    /**
     * 保存本阶段全部HTTP下发结果并检查下发结果是否已经足以结束总任务。
     */
    private void saveDispatchResults(
            String taskNo,
            Integer stepNo,
            List<CompletableFuture<DispatchResult>> dispatchFutures) {

        for (CompletableFuture<DispatchResult> future : dispatchFutures) {

            DispatchResult result = future.join();

            if (OperationTaskStepStatusEnum.DISPATCHED == result.status) {
                operationTaskService.markDispatched(
                        result.taskNo,
                        result.stepNo,
                        result.executorId
                );
            } else if (OperationTaskStepStatusEnum.FAILED == result.status) {
                operationTaskService.markRejected(
                        result.taskNo,
                        result.stepNo,
                        result.executorId,
                        result.reason
                );
            } else {
                operationTaskService.markDispatchResultUnknown(
                        result.taskNo,
                        result.stepNo,
                        result.executorId,
                        result.reason
                );
            }
        }

        // 检查本次阶段下发结果是否已经足以结束总任务。
        operationTaskService.finishDispatchStage(taskNo, stepNo);
    }

    /**
     * 单台执行器的HTTP下发结果。
     */
    private static class DispatchResult {

        private final String taskNo;
        private final Integer stepNo;
        private final String executorId;
        private final OperationTaskStepStatusEnum status;
        private final String reason;

        private DispatchResult(
                OperationTaskStep step,
                OperationTaskStepStatusEnum status,
                String reason) {
            this.taskNo = step.getTaskNo();
            this.stepNo = step.getStepNo();
            this.executorId = step.getExecutorId();
            this.status = status;
            this.reason = reason;
        }

        private static DispatchResult dispatched(OperationTaskStep step) {
            return new DispatchResult(
                    step,
                    OperationTaskStepStatusEnum.DISPATCHED,
                    null
            );
        }

        private static DispatchResult failed(
                OperationTaskStep step,
                String reason) {
            return new DispatchResult(
                    step,
                    OperationTaskStepStatusEnum.FAILED,
                    reason
            );
        }

        private static DispatchResult resultUnknown(
                OperationTaskStep step,
                String reason) {
            return new DispatchResult(
                    step,
                    OperationTaskStepStatusEnum.RESULT_UNKNOWN,
                    reason
            );
        }
    }
}

