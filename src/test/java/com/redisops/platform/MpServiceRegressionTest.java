package com.redisops.platform;

import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.redisops.platform.agent.config.AgentPlatformProperties;
import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.configsnapshot.mapper.ExecutorConfigSnapshotMapper;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotUploadRequest;
import com.redisops.platform.agent.configsnapshot.service.impl.ExecutorConfigSnapshotServiceImpl;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.mapper.ExecutorInfoMapper;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.agent.service.impl.AgentRegistrationServiceImpl;
import com.redisops.platform.agent.status.entity.ExecutorRuntimeStatus;
import com.redisops.platform.agent.status.mapper.ExecutorRuntimeStatusMapper;
import com.redisops.platform.agent.status.model.MetricReportRequest;
import com.redisops.platform.agent.status.service.impl.ExecutorRuntimeStatusServiceImpl;
import com.redisops.platform.security.service.ConfigCipherService;
import com.redisops.platform.security.service.TokenCipherService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MP 服务重构后，保留数据更新范围、更新条数和配置保护语义。 */
class MpServiceRegressionTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnActualOfflineUpdateCountAndKeepTimeoutConditions() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExecutorInfo.class);
        ExecutorInfoMapper mapper = mock(ExecutorInfoMapper.class);
        when(mapper.update(any(), any())).thenReturn(3);
        AgentRegistrationServiceImpl service = new AgentRegistrationServiceImpl(new AgentPlatformProperties(), mock(TokenCipherService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertEquals(3, service.markTimedOutExecutorsOffline());

        ArgumentCaptor<LambdaUpdateWrapper> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(any(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("executor_status ="));
        assertTrue(wrapper.getValue().getSqlSegment().contains("last_heartbeat_time <"));
        assertTrue(wrapper.getValue().getSqlSet().contains("executor_status="));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClearMissingMetricFieldsOnlyForReportingExecutor() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExecutorRuntimeStatus.class);
        ExecutorRuntimeStatusMapper mapper = mock(ExecutorRuntimeStatusMapper.class);
        when(mapper.selectOne(any(), eq(true))).thenReturn(new ExecutorRuntimeStatus());
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        ExecutorRuntimeStatusServiceImpl service = new ExecutorRuntimeStatusServiceImpl(registration);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        MetricReportRequest request = new MetricReportRequest();
        request.setAgentId("AGENT-A");
        MetricReportRequest.MetricPayload metric = new MetricReportRequest.MetricPayload();
        metric.setCollectTime(123L);
        metric.setStatus("up");
        metric.setRole("master");
        request.setMetric(metric);

        service.receive("Bearer test-token", request);

        ArgumentCaptor<LambdaUpdateWrapper> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(any(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("executor_id ="));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("AGENT-A"));
        assertTrue(wrapper.getValue().getSqlSet().contains("redis_master_host="));
        assertTrue(wrapper.getValue().getSqlSet().contains("sentinel_master_host="));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(null));
        verify(registration).touchLastReportTime("AGENT-A");
        verify(mapper, never()).insert(any(ExecutorRuntimeStatus.class));
    }

    @Test
    void shouldRejectChangedBackupWithoutOverwritingExistingSnapshot() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExecutorConfigSnapshot.class);
        ExecutorConfigSnapshotMapper mapper = mock(ExecutorConfigSnapshotMapper.class);
        ExecutorConfigSnapshot snapshot = new ExecutorConfigSnapshot();
        snapshot.setRedisConfigSha256("different-from-upload");
        when(mapper.selectOne(any(), eq(true))).thenReturn(snapshot);
        ExecutorConfigSnapshotServiceImpl service = new ExecutorConfigSnapshotServiceImpl(mock(AgentRegistrationService.class), mock(ConfigCipherService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ConfigSnapshotUploadRequest request = new ConfigSnapshotUploadRequest();
        request.setAgentId("AGENT-A");
        request.setRedisConfigBase64(Base64.getEncoder().encodeToString(
                "redis-config".getBytes(StandardCharsets.UTF_8)));
        request.setSentinelConfigBase64(Base64.getEncoder().encodeToString(
                "sentinel-config".getBytes(StandardCharsets.UTF_8)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.initializeOrVerify("Bearer test-token", request));

        assertTrue(error.getMessage().contains("禁止覆盖平台快照"));
        verify(mapper, never()).update(any(), any());
        verify(mapper, never()).insert(any(ExecutorConfigSnapshot.class));
    }
}
