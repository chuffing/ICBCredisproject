package com.danuvius.platform.service.impl;

import com.danuvius.platform.common.exception.AgentTaskDispatchUnknownException;
import com.danuvius.platform.common.exception.AgentTaskRejectedException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.AgentTaskRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.NodeConfigFileService;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.util.http.AgentTaskClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证同一阶段多执行器下发结果能够全部保存后再统一汇总。 */
class OperationTaskDispatchServiceImplTest {

    @Test
    void shouldSaveSuccessRejectedAndUnknownBeforeFinishingStage() {
        OperationTaskService taskService = mock(OperationTaskService.class);
        ExecutorInfoService executorService = mock(ExecutorInfoService.class);
        AgentTaskClient client = mock(AgentTaskClient.class);
        List<OperationTaskStep> steps = Arrays.asList(
                step("executor-a"), step("executor-b"), step("executor-c"));
        List<ExecutorInfo> executors = Arrays.asList(
                executor("executor-a", StatusEnum.ONLINE.getCode()),
                executor("executor-b", StatusEnum.ONLINE.getCode()),
                executor("executor-c", StatusEnum.ONLINE.getCode()));

        when(taskService.getStageSteps("TASK202610260001", 1)).thenReturn(steps);
        when(taskService.startStageDispatch("TASK202610260001", 1, steps))
                .thenReturn(steps);
        when(executorService.list(any())).thenReturn(executors);
        doAnswer(invocation -> {
            ExecutorInfo executor = invocation.getArgument(0);
            if ("executor-b".equals(executor.getId())) {
                throw new AgentTaskRejectedException("明确拒绝");
            }
            if ("executor-c".equals(executor.getId())) {
                throw new AgentTaskDispatchUnknownException("结果未知");
            }
            return null;
        }).when(client).dispatchTask(any(), any(AgentTaskRequestDTO.class));

        Executor directExecutor = Runnable::run;
        OperationTaskDispatchServiceImpl service = new OperationTaskDispatchServiceImpl(
                taskService, executorService, client,
                mock(RedisVersionPackageMapper.class),
                mock(NodeConfigFileService.class), directExecutor);

        service.dispatchStage("TASK202610260001", 1);

        InOrder order = inOrder(taskService);
        order.verify(taskService).markDispatched("TASK202610260001", 1, "executor-a");
        order.verify(taskService).markRejected(
                "TASK202610260001", 1, "executor-b", "明确拒绝");
        order.verify(taskService).markDispatchResultUnknown(
                "TASK202610260001", 1, "executor-c", "结果未知");
        order.verify(taskService).finishDispatchStage("TASK202610260001", 1);
    }

    @Test
    void shouldRejectWholeStageBeforeSendingHttpWhenAnyExecutorIsOffline() {
        OperationTaskService taskService = mock(OperationTaskService.class);
        ExecutorInfoService executorService = mock(ExecutorInfoService.class);
        AgentTaskClient client = mock(AgentTaskClient.class);
        List<OperationTaskStep> steps = Arrays.asList(
                step("executor-a"), step("executor-b"), step("executor-c"));

        when(taskService.getStageSteps("TASK202610260001", 1)).thenReturn(steps);
        when(executorService.list(any())).thenReturn(Arrays.asList(
                executor("executor-a", StatusEnum.ONLINE.getCode()),
                executor("executor-b", StatusEnum.OFFLINE.getCode()),
                executor("executor-c", StatusEnum.ONLINE.getCode())));

        OperationTaskDispatchServiceImpl service = new OperationTaskDispatchServiceImpl(
                taskService, executorService, client,
                mock(RedisVersionPackageMapper.class),
                mock(NodeConfigFileService.class), Runnable::run);

        service.dispatchStage("TASK202610260001", 1);

        ArgumentCaptor<Map<String, String>> reasons = ArgumentCaptor.forClass(Map.class);
        verify(taskService).rejectStageBeforeDispatch(
                eq("TASK202610260001"), eq(1), eq(steps), reasons.capture());
        assertEquals(1, reasons.getValue().size());
        assertTrue(reasons.getValue().get("executor-b").contains("不在线"));
        verify(taskService, never()).startStageDispatch(any(), any(), any());
        verify(client, never()).dispatchTask(any(), any());
    }

    private OperationTaskStep step(String executorId) {
        OperationTaskStep step = new OperationTaskStep();
        step.setTaskNo("TASK202610260001");
        step.setStepNo(1);
        step.setExecutorId(executorId);
        step.setOperationType("START_REDIS");
        step.setRequestParams("{}");
        return step;
    }

    private ExecutorInfo executor(String id, String status) {
        ExecutorInfo executor = new ExecutorInfo();
        executor.setId(id);
        executor.setHostIp("127.0.0.1");
        executor.setExecutorPort("9101");
        executor.setExecutorToken("token-" + id);
        executor.setExecutorStatus(status);
        return executor;
    }
}
