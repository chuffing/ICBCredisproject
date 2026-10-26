package com.danuvius.platform.service.impl;

import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.OperationTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证步骤事实、阶段汇总和下一阶段下发之间的事务边界。 */
class OperationTaskResultServiceImplTest {

    @Test
    void shouldSaveStepBeforeFinishingAndDispatchingNextStage() {
        OperationTaskService taskService = mock(OperationTaskService.class);
        OperationTaskDispatchExecutor dispatcher = mock(OperationTaskDispatchExecutor.class);
        TaskResultReportDTO report = report();
        when(taskService.finishStage(report.getTaskNo(), report.getStepNo()))
                .thenReturn(2);

        new OperationTaskResultServiceImpl(taskService, dispatcher)
                .reportTaskResult(report);

        InOrder order = inOrder(taskService, dispatcher);
        order.verify(taskService).saveStepResult(report);
        order.verify(taskService).finishStage(report.getTaskNo(), report.getStepNo());
        order.verify(dispatcher).dispatchStage(report.getTaskNo(), 2);
    }

    @Test
    void shouldKeepSavedStepAndMarkTaskUnknownWhenAggregationFails() {
        OperationTaskService taskService = mock(OperationTaskService.class);
        OperationTaskDispatchExecutor dispatcher = mock(OperationTaskDispatchExecutor.class);
        TaskResultReportDTO report = report();
        doThrow(new IllegalStateException("database unavailable"))
                .when(taskService).finishStage(report.getTaskNo(), report.getStepNo());

        new OperationTaskResultServiceImpl(taskService, dispatcher)
                .reportTaskResult(report);

        verify(taskService).saveStepResult(report);
        verify(taskService).markTaskResultUnknown(
                report.getTaskNo(), "阶段1结果汇总失败，请人工确认");
        verify(dispatcher, never()).dispatchStage(report.getTaskNo(), 2);
    }

    @Test
    void shouldReturnOriginalAggregationFailureWhenUnknownStatusAlsoCannotBeSaved() {
        OperationTaskService taskService = mock(OperationTaskService.class);
        OperationTaskDispatchExecutor dispatcher = mock(OperationTaskDispatchExecutor.class);
        TaskResultReportDTO report = report();
        IllegalStateException aggregationFailure =
                new IllegalStateException("aggregation failed");
        doThrow(aggregationFailure).when(taskService)
                .finishStage(report.getTaskNo(), report.getStepNo());
        doThrow(new IllegalStateException("status update failed"))
                .when(taskService).markTaskResultUnknown(
                report.getTaskNo(), "阶段1结果汇总失败，请人工确认");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new OperationTaskResultServiceImpl(taskService, dispatcher)
                        .reportTaskResult(report));

        assertEquals(aggregationFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
    }

    @Test
    void shouldNotAggregateWhenTimeoutUpdateLostTheRace() {
        OperationTaskService taskService = mock(OperationTaskService.class);
        OperationTaskDispatchExecutor dispatcher = mock(OperationTaskDispatchExecutor.class);
        when(taskService.markStepResultUnknown(
                "TASK202610260001", 1, "executor-1", "timeout"))
                .thenReturn(false);

        boolean updated = new OperationTaskResultServiceImpl(taskService, dispatcher)
                .markResultUnknown("TASK202610260001", 1, "executor-1", "timeout");

        assertFalse(updated);
        verify(taskService, never()).finishStage("TASK202610260001", 1);
    }

    private TaskResultReportDTO report() {
        TaskResultReportDTO report = new TaskResultReportDTO();
        report.setTaskNo("TASK202610260001");
        report.setStepNo(1);
        report.setAgentId("executor-1");
        report.setOperationType("START_REDIS");
        report.setStatus("SUCCESS");
        report.setResult("{\"success\":true,\"message\":\"ok\","
                + "\"startTime\":1792947600000,\"endTime\":1792947601000}");
        return report;
    }
}
