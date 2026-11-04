package com.danuvius.platform.service.impl;

import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.OperationTaskMapper;
import com.danuvius.platform.mapper.OperationTaskStepMapper;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.operation.TaskResultReportDTO;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.enums.instance.ManagementStatusEnum;
import com.danuvius.platform.model.enums.operation.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.operation.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.operation.OperationTaskTypeEnum;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.TaskNoService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证任务步骤状态汇总和执行器毫秒时间戳的核心规则。 */
class OperationTaskServiceImplTest {

    private OperationTaskMapper taskMapper;
    private OperationTaskStepMapper stepMapper;
    private ServerSwitchCompletionService switchCompletionService;
    private RedisInstanceService redisInstanceService;
    private RedisVersionPackageMapper versionPackageMapper;
    private Cache<String, Object> caffeineCache;
    private OperationTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(OperationTaskMapper.class);
        stepMapper = mock(OperationTaskStepMapper.class);
        switchCompletionService = mock(ServerSwitchCompletionService.class);
        redisInstanceService = mock(RedisInstanceService.class);
        versionPackageMapper = mock(RedisVersionPackageMapper.class);
        caffeineCache = Caffeine.newBuilder().build();
        service = new OperationTaskServiceImpl(
                taskMapper,
                stepMapper,
                mock(AuditLogService.class),
                mock(TaskNoService.class),
                switchCompletionService,
                redisInstanceService,
                versionPackageMapper,
                caffeineCache);
    }

    @Test
    void shouldWaitWhenAnyStepHasNotReachedTerminalStatus() {
        OperationTask task = runningTask();
        when(taskMapper.selectTaskForUpdate(task.getTaskNo())).thenReturn(task);
        when(stepMapper.selectTaskStageSteps(task.getTaskNo(), 1)).thenReturn(
                Arrays.asList(step("a", OperationTaskStepStatusEnum.SUCCESS),
                        step("b", OperationTaskStepStatusEnum.DISPATCHED),
                        step("c", OperationTaskStepStatusEnum.SUCCESS)));

        assertNull(service.finishStage(task.getTaskNo(), 1));

        verify(taskMapper, never()).updateTaskExecutionState(any());
        verify(stepMapper, never()).selectNextStepNo(task.getTaskNo(), 1);
    }

    @Test
    void shouldPreferResultUnknownWhenAllStepsAreTerminal() {
        OperationTask task = runningTask();
        when(taskMapper.selectTaskForUpdate(task.getTaskNo())).thenReturn(task);
        when(stepMapper.selectTaskStageSteps(task.getTaskNo(), 1)).thenReturn(
                Arrays.asList(step("a", OperationTaskStepStatusEnum.FAILED),
                        step("b", OperationTaskStepStatusEnum.RESULT_UNKNOWN),
                        step("c", OperationTaskStepStatusEnum.SUCCESS)));
        when(taskMapper.updateTaskExecutionState(task)).thenReturn(1);

        assertNull(service.finishStage(task.getTaskNo(), 1));

        assertEquals(OperationTaskStatusEnum.RESULT_UNKNOWN.name(), task.getTaskStatus());
        verify(stepMapper, never()).selectNextStepNo(task.getTaskNo(), 1);
    }

    @Test
    void shouldReturnNextStageOnlyAfterAllCurrentStepsSucceed() {
        OperationTask task = runningTask();
        when(taskMapper.selectTaskForUpdate(task.getTaskNo())).thenReturn(task);
        when(stepMapper.selectTaskStageSteps(task.getTaskNo(), 1)).thenReturn(
                Arrays.asList(step("a", OperationTaskStepStatusEnum.SUCCESS),
                        step("b", OperationTaskStepStatusEnum.SUCCESS),
                        step("c", OperationTaskStepStatusEnum.SUCCESS)));
        when(stepMapper.selectNextStepNo(task.getTaskNo(), 1)).thenReturn(2);

        assertEquals(Integer.valueOf(2), service.finishStage(task.getTaskNo(), 1));

        verify(taskMapper, never()).updateTaskExecutionState(any());
        verify(switchCompletionService, never()).completeIfNecessary(task);
    }

    @Test
    void shouldAcceptFastCallbackDuringDispatchingAndUseShanghaiTimezone() {
        OperationTask task = runningTask();
        OperationTaskStep step = step("executor-1", OperationTaskStepStatusEnum.DISPATCHING);
        when(taskMapper.selectTaskForUpdate(task.getTaskNo())).thenReturn(task);
        when(stepMapper.selectTaskStepForUpdate(task.getTaskNo(), 1, "executor-1"))
                .thenReturn(step);
        when(stepMapper.updateTaskStepResult(step)).thenReturn(1);

        long start = 1792947600000L;
        long end = start + 1000L;
        TaskResultReportDTO report = new TaskResultReportDTO();
        report.setTaskNo(task.getTaskNo());
        report.setStepNo(1);
        report.setAgentId("executor-1");
        report.setOperationType("START_REDIS");
        report.setStatus(OperationTaskStepStatusEnum.SUCCESS.name());
        report.setResult("{\"success\":true,\"errorCode\":null,"
                + "\"message\":\"ok\",\"startTime\":" + start
                + ",\"endTime\":" + end + "}");

        service.saveStepResult(report);

        assertEquals(OperationTaskStepStatusEnum.SUCCESS.name(), step.getStepStatus());
        assertEquals(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(start), ZoneId.of("Asia/Shanghai")),
                step.getStartTime());
        assertEquals(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(end), ZoneId.of("Asia/Shanghai")),
                step.getEndTime());
        assertEquals("", step.getErrorCode());
    }

    @Test
    void shouldRejectDuplicateResultAfterStepReachedTerminalStatus() {
        OperationTask task = runningTask();
        OperationTaskStep step = step("executor-1", OperationTaskStepStatusEnum.SUCCESS);
        when(taskMapper.selectTaskForUpdate(task.getTaskNo())).thenReturn(task);
        when(stepMapper.selectTaskStepForUpdate(task.getTaskNo(), 1, "executor-1"))
                .thenReturn(step);

        TaskResultReportDTO report = new TaskResultReportDTO();
        report.setTaskNo(task.getTaskNo());
        report.setStepNo(1);
        report.setAgentId("executor-1");
        report.setOperationType("START_REDIS");
        report.setStatus(OperationTaskStepStatusEnum.SUCCESS.name());
        report.setResult("{\"success\":true,\"message\":\"ok\","
                + "\"startTime\":1,\"endTime\":2}");

        assertThrows(BusinessException.class, () -> service.saveStepResult(report));
        verify(stepMapper, never()).updateTaskStepResult(any());
    }

    @Test
    void shouldInvalidateVersionCachesOnlyAfterTransactionCommit() {
        OperationTask task = runningTask();
        task.setTaskType(OperationTaskTypeEnum.VERSION_UPGRADE.name());
        task.setRequestParams("{\"packageId\":1}");

        when(taskMapper.selectTaskForUpdate(task.getTaskNo())).thenReturn(task);
        when(stepMapper.selectTaskStageSteps(task.getTaskNo(), 1)).thenReturn(
                Arrays.asList(step("a", OperationTaskStepStatusEnum.SUCCESS),
                        step("b", OperationTaskStepStatusEnum.SUCCESS),
                        step("c", OperationTaskStepStatusEnum.SUCCESS)));
        when(stepMapper.selectNextStepNo(task.getTaskNo(), 1)).thenReturn(null);
        when(taskMapper.updateTaskExecutionState(task)).thenReturn(1);

        RedisVersionPackage versionPackage = new RedisVersionPackage();
        versionPackage.setId(1L);
        versionPackage.setRedisVersion("6.2.20");
        when(versionPackageMapper.selectById(1L)).thenReturn(versionPackage);

        RedisInstance first = new RedisInstance();
        RedisInstance second = new RedisInstance();
        RedisInstance third = new RedisInstance();
        when(redisInstanceService.list(any())).thenReturn(
                Arrays.asList(first, second, third));
        when(redisInstanceService.updateBatchById(any())).thenReturn(true);

        String instanceListKey = CacheKeyUtil.instanceList(
                ManagementStatusEnum.ACTIVE.getCode());
        caffeineCache.put(instanceListKey, "old-instance-list");
        caffeineCache.put(CacheKeyUtil.TOPOLOGY, "old-topology");
        caffeineCache.put(CacheKeyUtil.NODES, "old-nodes");

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertNull(service.finishStage(task.getTaskNo(), 1));

            assertEquals("old-instance-list",
                    caffeineCache.getIfPresent(instanceListKey));
            assertEquals("old-topology",
                    caffeineCache.getIfPresent(CacheKeyUtil.TOPOLOGY));
            assertEquals("old-nodes",
                    caffeineCache.getIfPresent(CacheKeyUtil.NODES));

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            assertNull(caffeineCache.getIfPresent(instanceListKey));
            assertNull(caffeineCache.getIfPresent(CacheKeyUtil.TOPOLOGY));
            assertNull(caffeineCache.getIfPresent(CacheKeyUtil.NODES));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OperationTask runningTask() {
        OperationTask task = new OperationTask();
        task.setTaskNo("TASK202610260001");
        task.setTaskType(OperationTaskTypeEnum.FAULT_RECOVERY.name());
        task.setTaskStatus(OperationTaskStatusEnum.RUNNING.name());
        task.setOperationReason("test");
        task.setCreatedBy("tester");
        return task;
    }

    private OperationTaskStep step(
            String executorId,
            OperationTaskStepStatusEnum status) {
        OperationTaskStep step = new OperationTaskStep();
        step.setTaskNo("TASK202610260001");
        step.setStepNo(1);
        step.setExecutorId(executorId);
        step.setOperationType("START_REDIS");
        step.setStepStatus(status.name());
        step.setFailureReason(status == OperationTaskStepStatusEnum.FAILED
                ? "failed" : "");
        return step;
    }
}
