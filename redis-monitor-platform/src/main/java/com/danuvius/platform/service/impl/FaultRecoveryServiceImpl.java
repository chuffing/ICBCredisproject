package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.FaultRecoveryConstants;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.dto.FaultRecoveryRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.model.vo.FaultRecoveryResponseVO;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.FaultRecoveryService;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 故障恢复服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaultRecoveryServiceImpl implements FaultRecoveryService {

    private final RedisInstanceService redisInstanceService;

    private final SentinelNodeInfoService sentinelNodeInfoService;

    private final ExecutorInfoService executorInfoService;

    private final OperationTaskService operationTaskService;

    private final OperationTaskDispatchExecutor
            operationTaskDispatchExecutor;

    /**
     * 对当前纳管的Redis环境执行故障恢复。
     *
     * @param request 故障恢复请求
     * @return 故障恢复结果
     */
    @Override
    @Transactional
    public FaultRecoveryResponseVO faultRecovery(
            FaultRecoveryRequestDTO request) {

        // 1. 查询并校验故障恢复需要的环境信息。
        RecoveryContext context = loadRecoveryContext();

        // 2. 检查其他高风险任务并创建故障恢复任务。
        OperationTask task = operationTaskService.createTask(
                OperationTaskTypeEnum.FAULT_RECOVERY,
                request.getOperationReason(),
                request.getCreatedBy(),
                JSON.toJSONString(request)
        );

        // 3. 生成并保存本次故障恢复的全部执行步骤。
        List<OperationTaskStep> steps = buildRecoverySteps(
                task.getTaskNo(),
                context
        );
        operationTaskService.saveSteps(steps);

        // 4. 当前事务提交后，异步下发故障恢复的第一个阶段。避免任务步骤尚未成功保存，就开始向执行器下发。
        registerStageDispatchAfterCommit(
                task.getTaskNo(),
                FaultRecoveryConstants.STOP_SENTINEL_STEP_NO
        );

        // 5. 立即向页面返回任务信息，不等待执行器完成恢复。
        return new FaultRecoveryResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                FaultRecoveryConstants.TASK_CREATED_MESSAGE,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 查询故障恢复任务状态。
     *
     * @param taskNo 故障恢复任务编号
     * @return 故障恢复任务状态
     */
    @Override
    public FaultRecoveryResponseVO getFaultRecoveryStatus(String taskNo) {

        OperationTask task = operationTaskService.getTaskByTaskNo(taskNo);

        // 任务不存在或不是故障恢复任务时，统一按照未找到处理。
        if (task == null
                || !OperationTaskTypeEnum.FAULT_RECOVERY.name()
                .equals(task.getTaskType())) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到对应的故障恢复任务"
            );
        }

        return new FaultRecoveryResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                buildTaskStatusMessage(task.getTaskStatus()),
                task.getFailureReason(),
                task.getTerminationReason(),
                task.getTerminatedBy(),
                task.getTerminatedTime()
        );
    }

    /**
     * 根据故障恢复任务状态生成页面提示语。
     */
    private String buildTaskStatusMessage(String taskStatus) {

        OperationTaskStatusEnum status;

        try {
            status = OperationTaskStatusEnum.valueOf(taskStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "故障恢复任务状态不正确"
            );
        }

        switch (status) {
            case CREATED:
                return FaultRecoveryConstants.TASK_CREATED_MESSAGE;
            case RUNNING:
                return FaultRecoveryConstants.TASK_RUNNING_MESSAGE;
            case RESULT_UNKNOWN:
                return FaultRecoveryConstants.TASK_RESULT_UNKNOWN_MESSAGE;
            case MANUALLY_TERMINATED:
                return FaultRecoveryConstants
                        .TASK_MANUALLY_TERMINATED_MESSAGE;
            case SUCCESS:
                return FaultRecoveryConstants.TASK_SUCCESS_MESSAGE;
            case FAILED:
                return FaultRecoveryConstants.TASK_FAILED_MESSAGE;
            default:
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "故障恢复任务状态不正确"
                );
        }
    }

    /**
     * 注册事务提交后的阶段调度，避免任务步骤尚未落库就开始下发。
     */
    private void registerStageDispatchAfterCommit(
            String taskNo,
            Integer stepNo) {

        TransactionSynchronizationManager.registerSynchronization(

                // 创建一个事务回调对象。
                new TransactionSynchronization() {

                    // afterCommit()表示：当前数据库事务成功提交之后，执行这里的代码。
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

    /**
     * 查询并校验当前纳管的Redis和Sentinel环境。
     */
    private RecoveryContext loadRecoveryContext() {

        // 1. 从数据库读取当前已纳管的Redis实例。
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>()
                        .eq(
                                RedisInstance::getManagementStatus,
                                StatusEnum.ACTIVE.getCode()
                        )
        );

        // 必须有三台已纳管Redis示例。
        if (redisInstances.size()
                != FaultRecoveryConstants.REQUIRED_REDIS_INSTANCE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "故障恢复要求当前环境中正好存在"
                            + FaultRecoveryConstants.REQUIRED_REDIS_INSTANCE_COUNT
                            + "台已纳管Redis实例"
            );
        }

        // 2. 从数据库读取当前已纳管的Sentinel节点。
        List<SentinelNodeInfo> sentinelNodes = sentinelNodeInfoService.list(
                new LambdaQueryWrapper<SentinelNodeInfo>()
                        .eq(
                                SentinelNodeInfo::getManagementStatus,
                                StatusEnum.ACTIVE.getCode()
                        )
        );

        // 必须有三台已纳管Sentinel。
        if (sentinelNodes.size()
                != FaultRecoveryConstants.REQUIRED_SENTINEL_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "故障恢复要求当前环境中正好存在"
                            + FaultRecoveryConstants.REQUIRED_SENTINEL_NODE_COUNT
                            + "台已纳管Sentinel节点"
            );
        }

        // 3. 从数据库读取当前已纳管的执行器。
        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(
                                ExecutorInfo::getManagementStatus,
                                StatusEnum.ACTIVE.getCode()
                        )
        );

        // 必须有三台已纳管执行器。
        if (executors.size()
                != FaultRecoveryConstants.REQUIRED_EXECUTOR_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "故障恢复要求当前环境中正好存在"
                            + FaultRecoveryConstants.REQUIRED_EXECUTOR_COUNT
                            + "台已纳管执行器"
            );
        }

        // 三台已纳管执行器必须全部在线，否则无法下发故障恢复任务。
        for (ExecutorInfo executor : executors) {
            if (!StatusEnum.ONLINE.getCode()
                    .equals(executor.getExecutorStatus())) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "故障恢复要求三台已纳管执行器全部在线，"
                                + "请人工检查执行器状态"
                );
            }
        }

        // 4. 将同一台服务器上的Redis、Sentinel和执行器组合成恢复节点。
        return buildRecoveryContext(redisInstances, sentinelNodes, executors);
    }

    /**
     * 按服务器IP组合Redis、Sentinel和执行器节点。
     */
    private RecoveryContext buildRecoveryContext(
            List<RedisInstance> redisInstances,
            List<SentinelNodeInfo> sentinelNodes,
            List<ExecutorInfo> executors) {

        // ======== 1. 建立Sentinel索引 ========
        // String：键（Key），表示服务器IP。
        // SentinelNodeInfo：值（Value），表示Sentinel节点对象。
        Map<String, SentinelNodeInfo> sentinelByHost = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : sentinelNodes) {
            String hostIp = StringUtils.trimToNull(sentinelNode.getNodeIp());
            if (hostIp == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管Sentinel节点的服务器IP不能为空"
                );
            }
            // 存入节点并检查IP是否重复。
            if (sentinelByHost.put(hostIp, sentinelNode) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一服务器IP存在多个已纳管Sentinel节点：" + hostIp
                );
            }
        }

        // ======== 2. 建立执行器索引 ========
        // 只使用数据库中已纳管的执行器，并按照服务器IP建立对应关系。
        Map<String, ExecutorInfo> executorByHost = new HashMap<>();
        for (ExecutorInfo executor : executors) {
            String hostIp = StringUtils.trimToNull(executor.getHostIp());
            if (hostIp == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管执行器的服务器IP不能为空"
                );
            }

            if (StringUtils.trimToNull(executor.getId()) == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "服务器上的执行器编号不能为空：" + hostIp
                );
            }

            // 同一台服务器只能有一个已纳管执行器参与故障恢复。
            if (executorByHost.put(hostIp, executor) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一服务器IP存在多个已纳管执行器：" + hostIp
                );
            }
        }

        // ======== 3. 组合故障恢复节点 ========
        // 用于保存配对完成的Redis和Sentinel节点。
        List<RecoveryNode> recoveryNodes = new ArrayList<>();
        // 用来记录已经遍历过的Redis服务器IP，防止IP重复。
        Set<String> redisHosts = new HashSet<>();
        for (RedisInstance redisInstance : redisInstances) {
            String hostIp = StringUtils.trimToNull(redisInstance.getHostIp());
            if (hostIp == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管Redis实例的服务器IP不能为空"
                );
            }
            // 添加元素并检查重复。
            if (!redisHosts.add(hostIp)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一服务器IP存在多个已纳管Redis实例：" + hostIp
                );
            }

            // 从sentinelByHost这个Map中，根据当前Redis的服务器IP查找Sentinel。
            SentinelNodeInfo sentinelNode = sentinelByHost.remove(hostIp);
            if (sentinelNode == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis实例所在服务器缺少已纳管Sentinel节点：" + hostIp
                );
            }

            // 根据Redis服务器IP查找同机执行器。
            ExecutorInfo executor = executorByHost.get(hostIp);
            if (executor == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis实例所在服务器缺少已纳管执行器：" + hostIp
                );
            }

            // 校验故障恢复备份配置文件的SHA256摘要。
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

            RecoveryNode recoveryNode = new RecoveryNode(
                    redisInstance,
                    sentinelNode,
                    executor
            );
            recoveryNodes.add(recoveryNode);
        }

        // 4. Redis遍历完成后仍有Sentinel未匹配，说明两类节点不在同一组服务器上。
        if (!sentinelByHost.isEmpty()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "存在未匹配Redis实例的已纳管Sentinel节点"
            );
        }

        return new RecoveryContext(recoveryNodes);
    }

    /**
     * 为故障恢复任务生成全部执行步骤。
     *
     * @param taskNo 运维任务编号
     * @param context 故障恢复环境
     * @return 故障恢复执行步骤
     */
    private List<OperationTaskStep> buildRecoverySteps(
            String taskNo,
            RecoveryContext context) {

        List<OperationTaskStep> steps = new ArrayList<>();

        // ======== 1. 停止三台Sentinel ========
        for (RecoveryNode node : context.recoveryNodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.STOP_SENTINEL_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.STOP_SENTINEL,
                    null
            ));
        }

        // ======== 2. 停止三台Redis ========
        for (RecoveryNode node : context.recoveryNodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.STOP_REDIS_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.STOP_REDIS,
                    null
            ));
        }

        // ======== 3. 恢复三台服务器上的配置文件 ========
        for (RecoveryNode node : context.recoveryNodes) {
            // 将本机Redis和Sentinel备份配置的摘要传给执行器。
            // 执行器恢复前会再次校验本地备份，防止恢复错误文件。
            Map<String, String> restoreConfigParams = new HashMap<>();
            restoreConfigParams.put(
                    FaultRecoveryConstants.REDIS_CONFIG_SHA256_PARAM,
                    node.redisInstance.getConfigSha256()
            );
            restoreConfigParams.put(
                    FaultRecoveryConstants.SENTINEL_CONFIG_SHA256_PARAM,
                    node.sentinelNode.getConfigSha256()
            );

            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.RESTORE_CONFIG_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.RESTORE_CONFIG,
                    JSON.toJSONString(restoreConfigParams)
            ));
        }

        // ======== 4. 启动三台Redis ========
        for (RecoveryNode node : context.recoveryNodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.START_REDIS_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.START_REDIS,
                    null
            ));
        }

        // ======== 5. 启动三台Sentinel ========
        for (RecoveryNode node : context.recoveryNodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.START_SENTINEL_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.START_SENTINEL,
                    null
            ));
        }

        // ======== 6. 验证运维操作完成后的Redis和Sentinel拓扑 ========
        // 三台执行器分别检查本机Redis和Sentinel，并在拓扑满足要求后上报成功。
        for (RecoveryNode node : context.recoveryNodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.VERIFY_TOPOLOGY_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.VERIFY_TOPOLOGY,
                    null
            ));
        }

        return steps;
    }

    /**
     * 创建单条故障恢复执行步骤。
     *
     * @param taskNo 运维任务编号
     * @param stepNo 阶段编号
     * @param node 故障恢复节点
     * @param operationType 执行器操作类型
     * @param requestParams 任务参数JSON
     * @return 故障恢复执行步骤
     */
    private OperationTaskStep createRecoveryStep(
            String taskNo,
            Integer stepNo,
            RecoveryNode node,
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
     * 校验故障恢复备份配置文件的SHA256摘要。
     */
    private void validateConfigSha256(
            String sha256,
            String configType,
            String hostIp) {

        String value = StringUtils.trimToNull(sha256);
        if (value == null
                || !ValidationConstants.SHA256_PATTERN
                .matcher(value)
                .matches()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    hostIp + "服务器的" + configType
                            + "备份配置SHA256不正确"
            );
        }
    }

    /**
     * 已完成基础校验的故障恢复环境。
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class RecoveryContext {

        private final List<RecoveryNode> recoveryNodes;
    }

    /**
     * 同一台服务器上的Redis、Sentinel和执行器恢复节点。
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class RecoveryNode {

        private final RedisInstance redisInstance;

        private final SentinelNodeInfo sentinelNode;

        private final ExecutorInfo executorInfo;
    }
}

