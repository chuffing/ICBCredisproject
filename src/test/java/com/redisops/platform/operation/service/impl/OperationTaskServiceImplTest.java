package com.redisops.platform.operation.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.client.AgentTaskClient;
import com.redisops.platform.operation.controller.TaskReportController;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.mapper.OperationTaskMapper;
import com.redisops.platform.operation.mapper.OperationTaskStepMapper;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.operation.service.TaskNoService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证平台能够核对执行器本地结果，并阻止未知步骤继续推进。 */
class OperationTaskServiceImplTest {

    /** HTTP 入口校验通过后，仍认证执行器、保存结果并安排下一组。 */
    @Test
    void shouldApplyValidatedHttpCallback() {
        TestContext context = context();
        when(context.stepMapper.selectOne(any())).thenReturn(dispatchedStep());
        when(context.stepMapper.update(any(), any())).thenReturn(1);
        TaskResultReportRequest request = successResult();

        assertEquals(200, new TaskReportController(context.service)
                .report("Bearer test-token", request).getStatusCodeValue());

        verify(context.registrationService).authenticateExecutor("redis-node-01", "Bearer test-token");
        verify(context.registrationService).touchLastReportTime("redis-node-01");
        verify(context.stepMapper).update(any(), any());
        verify(context.scheduler).schedule(any(Runnable.class), any(Date.class));
    }

    /** 主动查询绕过 Controller，非法结果仍不得进入步骤查询、更新和后续调度。 */
    @ParameterizedTest
    @ValueSource(strings = {"taskNo", "stepNo", "status", "operationType"})
    void shouldRejectMalformedQueriedResultBeforeApplyingIt(String invalidField) {
        TestContext context = context();
        OperationTaskStep step = dispatchedStep();
        LocalDateTime now = LocalDateTime.now();
        step.setLastDispatchTime(now.minusSeconds(30));
        when(context.stepMapper.selectList(any())).thenReturn(Collections.singletonList(step));
        when(context.registrationService.requireOnlineExecutor("redis-node-01")).thenReturn(executor());
        TaskResultReportRequest result = successResult();
        switch (invalidField) {
            case "taskNo": result.setTaskNo(" "); break;
            case "stepNo": result.setStepNo(0); break;
            case "status": result.setStatus("RUNNING"); break;
            case "operationType": result.setOperationType("OTHER"); break;
            default: throw new AssertionError("Unknown test field");
        }
        when(context.agentTaskClient.queryTaskResult(any(), any(), any())).thenReturn(result);

        context.service.reconcileUnconfirmedResults(now.minusSeconds(10), now.minusSeconds(120));

        verify(context.stepMapper, never()).selectOne(any());
        verify(context.stepMapper, never()).update(any(), any());
        verify(context.registrationService, never()).touchLastReportTime(any());
        verifyNoInteractions(context.scheduler);
    }

    /** 内部编排直接调用服务时，仍应拒绝空步骤、重复目标和不连续的步骤编号。 */
    @Test
    void shouldKeepTaskRulesForInternalServiceCallers() {
        for (int scenario = 0; scenario < 3; scenario++) {
            TestContext context = context();
            CreateOperationTaskRequest request = new CreateOperationTaskRequest();
            request.setTaskType("FAULT_RECOVERY");
            request.setCreatedBy("admin");
            request.setOperationReason("验证内部调用的任务规则");
            CreateOperationTaskStepRequest step = new CreateOperationTaskStepRequest();
            step.setStepNo(scenario == 2 ? 2 : 1);
            step.setExecutorId("AGENT-A");
            step.setOperationType("STOP_REDIS");
            List<CreateOperationTaskStepRequest> steps = new ArrayList<>();
            steps.add(scenario == 0 ? null : step);
            if (scenario == 1) {
                steps.add(step);
            }
            request.setSteps(steps);
            assertThrows(IllegalArgumentException.class, () -> context.service.createAndDispatch(request));
        }
    }

    /** MP 保存主任务后，应批量保存全部步骤，并且仅下发第一组。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistWholeTaskAndDispatchOnlyFirstGroup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                OperationTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                OperationTaskStep.class);
        OperationTaskMapper tasks = mock(OperationTaskMapper.class);
        OperationTaskStepMapper steps = mock(OperationTaskStepMapper.class);
        TaskNoService numbers = mock(TaskNoService.class);
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        AgentTaskClient client = mock(AgentTaskClient.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        when(numbers.nextTaskNo()).thenReturn("TASK-MP-1");
        when(tasks.selectCount(any())).thenReturn(0L);
        AtomicReference<OperationTask> savedTask = new AtomicReference<>();
        doAnswer(invocation -> {
            savedTask.set(invocation.getArgument(0));
            return 1;
        }).when(tasks).insert(any(OperationTask.class));
        when(tasks.selectOne(any(), eq(true))).thenAnswer(invocation -> savedTask.get());
        List<OperationTaskStep> savedSteps = new ArrayList<>();
        when(steps.insertBatch(any())).thenAnswer(invocation -> {
            List<OperationTaskStep> values = invocation.getArgument(0);
            for (int index = 0; index < values.size(); index++) {
                values.get(index).setId((long) index + 1);
            }
            savedSteps.addAll(values);
            return values.size();
        });
        when(steps.selectList(any())).thenReturn(savedSteps);
        when(steps.update(any(), any())).thenReturn(1);
        when(registration.requireOnlineExecutor(any())).thenReturn(new ExecutorInfo());
        OperationTaskServiceImpl service = new OperationTaskServiceImpl(steps, numbers,
                registration, client, new ObjectMapper(), transactions, mock(TaskScheduler.class),
                Collections.emptyList(), Collections.emptyList());
        ReflectionTestUtils.setField(service, "baseMapper", tasks);
        CreateOperationTaskRequest request = new CreateOperationTaskRequest();
        request.setTaskType("FAULT_RECOVERY");
        request.setCreatedBy("admin");
        request.setOperationReason("验证分组调度");
        List<CreateOperationTaskStepRequest> requestedSteps = new ArrayList<>();
        for (int group = 1; group <= 2; group++) {
            for (int node = 1; node <= 3; node++) {
                CreateOperationTaskStepRequest step = new CreateOperationTaskStepRequest();
                step.setStepNo(group);
                step.setExecutorId("AGENT-" + node);
                step.setOperationType(group == 1 ? "STOP_SENTINEL" : "STOP_REDIS");
                requestedSteps.add(step);
            }
        }
        request.setSteps(requestedSteps);

        OperationTaskDetail detail = service.createAndDispatch(request);

        assertEquals("TASK-MP-1", detail.getTask().getTaskNo());
        assertEquals(6, detail.getSteps().size());
        verify(steps).insertBatch(any());
        verify(client, times(3)).dispatch(any(), eq("TASK-MP-1"), eq(1), eq("STOP_SENTINEL"), any());
        verify(client, times(3)).dispatch(any(), any(), any(), any(), any());
    }

    /** 回调丢失但执行器保存了SUCCESS时，平台应补记结果并安排推进。 */
    @Test
    void shouldRecoverLostCallbackFromAgentResultQuery() {
        TestContext context = context();
        OperationTaskStep step = dispatchedStep();
        when(context.stepMapper.selectList(any())).thenReturn(Collections.singletonList(step));
        when(context.stepMapper.selectOne(any())).thenReturn(step);
        when(context.stepMapper.update(any(), any())).thenReturn(1);
        when(context.registrationService.requireOnlineExecutor("redis-node-01"))
                .thenReturn(executor());
        when(context.agentTaskClient.queryTaskResult(any(), any(), any()))
                .thenReturn(successResult());

        LocalDateTime now = LocalDateTime.now();
        context.service.reconcileUnconfirmedResults(
                now.minusSeconds(10), now.minusSeconds(120));

        verify(context.registrationService).touchLastReportTime("redis-node-01");
        verify(context.scheduler).schedule(any(Runnable.class), any(Date.class));
    }

    /** 超过阈值仍无法查询结果时，只标记RESULT_UNKNOWN，不安排下一组。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldMarkResultUnknownWithoutAdvancing() {
        TestContext context = context();
        OperationTaskStep step = dispatchedStep();
        when(context.stepMapper.selectList(any())).thenReturn(Collections.singletonList(step));
        when(context.registrationService.requireOnlineExecutor("redis-node-01"))
                .thenReturn(executor());
        when(context.agentTaskClient.queryTaskResult(any(), any(), any())).thenReturn(null);
        when(context.stepMapper.update(any(), any())).thenReturn(1);

        LocalDateTime now = LocalDateTime.now();
        context.service.reconcileUnconfirmedResults(
                now.minusSeconds(10), now.minusSeconds(120));

        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(context.stepMapper).update(any(), wrapperCaptor.capture());
        LambdaUpdateWrapper<OperationTaskStep> update =
                (LambdaUpdateWrapper<OperationTaskStep>) wrapperCaptor.getValue();
        assertTrue(update.getParamNameValuePairs().containsValue("RESULT_UNKNOWN"));
    }

    /** 创建只包含本测试所需依赖的运维任务服务。 */
    private TestContext context() {
        // 纯单元测试没有启动 MyBatis Spring 容器，需要手工初始化 Lambda 字段缓存。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                OperationTaskStep.class);
        TestContext context = new TestContext();
        context.stepMapper = mock(OperationTaskStepMapper.class);
        context.registrationService = mock(AgentRegistrationService.class);
        context.agentTaskClient = mock(AgentTaskClient.class);
        context.scheduler = mock(TaskScheduler.class);
        context.service = new OperationTaskServiceImpl(context.stepMapper,
                mock(TaskNoService.class),
                context.registrationService,
                context.agentTaskClient,
                new ObjectMapper(),
                mock(TransactionTemplate.class),
                context.scheduler,
                Collections.emptyList(),
                Collections.emptyList());
        ReflectionTestUtils.setField(context.service, "baseMapper", mock(OperationTaskMapper.class));
        return context;
    }

    /** 创建一个已经下发且超过核对阈值的步骤。 */
    private OperationTaskStep dispatchedStep() {
        OperationTaskStep step = new OperationTaskStep();
        step.setId(1L);
        step.setTaskNo("TASK202610100001");
        step.setStepNo(1);
        step.setExecutorId("redis-node-01");
        step.setOperationType("STOP_SENTINEL");
        step.setStepStatus("DISPATCHED");
        step.setLastDispatchTime(LocalDateTime.now().minusMinutes(5));
        return step;
    }

    /** 创建测试执行器记录。 */
    private ExecutorInfo executor() {
        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId("redis-node-01");
        executor.setHostIp("10.0.0.11");
        executor.setExecutorPort(9101);
        return executor;
    }

    /** 创建执行器主动查询接口返回的成功结果。 */
    private TaskResultReportRequest successResult() {
        TaskResultReportRequest result = new TaskResultReportRequest();
        result.setTaskNo("TASK202610100001");
        result.setStepNo(1);
        result.setAgentId("redis-node-01");
        result.setOperationType("STOP_SENTINEL");
        result.setStatus("SUCCESS");
        result.setResult("{\"success\":true,\"message\":\"Sentinel已停止\"}");
        return result;
    }

    /** 保存测试中需要复用的依赖。 */
    private static class TestContext {
        private OperationTaskStepMapper stepMapper;
        private AgentRegistrationService registrationService;
        private AgentTaskClient agentTaskClient;
        private TaskScheduler scheduler;
        private OperationTaskServiceImpl service;
    }
}
