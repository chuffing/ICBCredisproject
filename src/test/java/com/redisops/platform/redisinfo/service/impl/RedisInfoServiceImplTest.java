package com.redisops.platform.redisinfo.service.impl;

import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import com.redisops.platform.redisinfo.mapper.RedisInfoMapper;
import com.redisops.platform.security.service.ConfigCipherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证服务器切换成功后redis_info的新旧拓扑提交规则。 */
@ExtendWith(MockitoExtension.class)
class RedisInfoServiceImplTest {

    @Mock private RedisInfoMapper redisInfoMapper;
    @Mock private AgentRegistrationService agentRegistrationService;
    @Mock private ConfigCipherService configCipherService;

    /** 成功任务应退出旧拓扑并新增三台直接处于MANAGED状态的节点。 */
    @Test
    void shouldCommitNewManagedTopologyOnlyAfterSuccessfulTask() {
        // 纯单元测试没有启动Spring/MyBatis，需要手工初始化Lambda字段缓存。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), RedisInfo.class);
        List<RedisInfo> oldTopology = Arrays.asList(
                oldNode(1L, "OLD-1", "10.0.0.1", "MASTER"),
                oldNode(2L, "OLD-2", "10.0.0.2", "REPLICA"),
                oldNode(3L, "OLD-3", "10.0.0.3", "REPLICA"));
        List<RedisInfo> committedTopology = Arrays.asList(
                oldNode(4L, "NEW-1", "10.0.1.1", "MASTER"),
                oldNode(5L, "NEW-2", "10.0.1.2", "REPLICA"),
                oldNode(6L, "NEW-3", "10.0.1.3", "REPLICA"));
        when(redisInfoMapper.selectList(any())).thenReturn(oldTopology, committedTopology);

        RedisInfoServiceImpl service = new RedisInfoServiceImpl(agentRegistrationService,
                configCipherService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", redisInfoMapper);
        OperationTask task = new OperationTask();
        task.setTaskType("SERVER_SWITCH");
        task.setRequestParams("{" +
                "\"version\":\"7.2.5\"," +
                "\"masterHost\":\"10.0.1.1\"," +
                "\"redisPort\":6379," +
                "\"sentinelPort\":26379," +
                "\"nodes\":[" +
                "{\"executorId\":\"NEW-1\",\"hostIp\":\"10.0.1.1\",\"role\":\"MASTER\"}," +
                "{\"executorId\":\"NEW-2\",\"hostIp\":\"10.0.1.2\",\"role\":\"REPLICA\"}," +
                "{\"executorId\":\"NEW-3\",\"hostIp\":\"10.0.1.3\",\"role\":\"REPLICA\"}]}" );

        service.commitSuccessfulServerSwitch(task);

        verify(redisInfoMapper).update(any(), any());
        ArgumentCaptor<RedisInfo> inserted = ArgumentCaptor.forClass(RedisInfo.class);
        verify(redisInfoMapper, times(3)).insert(inserted.capture());
        assertEquals(Arrays.asList("NEW-1", "NEW-2", "NEW-3"),
                Arrays.asList(inserted.getAllValues().get(0).getExecutorId(),
                        inserted.getAllValues().get(1).getExecutorId(),
                        inserted.getAllValues().get(2).getExecutorId()));
        assertTrue(inserted.getAllValues().stream().allMatch(item ->
                "MANAGED".equals(item.getManagementStatus())
                        && "UP".equals(item.getRedisStatus())
                        && "UP".equals(item.getSentinelStatus())
                        && "encrypted-password".equals(item.getRedisPasswordCiphertext())));
    }

    /** 创建测试使用的Redis实例记录。 */
    private RedisInfo oldNode(Long id, String executorId, String hostIp, String role) {
        RedisInfo info = new RedisInfo();
        info.setId(id);
        info.setExecutorId(executorId);
        info.setHostIp(hostIp);
        info.setRedisRole(role);
        info.setManagementStatus("MANAGED");
        info.setRedisPasswordCiphertext("encrypted-password");
        return info;
    }
}
