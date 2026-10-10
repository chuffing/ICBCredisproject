package com.redisops.platform.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.client.AgentTaskClient;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.mapper.OperationTaskMapper;
import com.redisops.platform.operation.mapper.OperationTaskStepMapper;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.operation.service.TaskNoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证平台能够核对执行器本地结果，并阻止未知步骤继续推进。 */
class OperationTaskServiceImplTest {

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
        context.service = new OperationTaskServiceImpl(
                mock(OperationTaskMapper.class),
                context.stepMapper,
                mock(TaskNoService.class),
                context.registrationService,
                context.agentTaskClient,
                new ObjectMapper(),
                mock(TransactionTemplate.class),
                context.scheduler,
                Collections.emptyList(),
                Collections.emptyList());
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
