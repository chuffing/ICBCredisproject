package com.redisops.platform.redisinfo.service.impl;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import com.redisops.platform.redisinfo.mapper.RedisInfoMapper;
import com.redisops.platform.redisinfo.model.InitializeRedisInfoRequest;
import com.redisops.platform.redisinfo.model.RedisInfoNodeRequest;
import com.redisops.platform.redisinfo.service.RedisInfoService;
import com.redisops.platform.security.service.ConfigCipherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 使用redis_info保存平台认定的正式拓扑。
 */
@Service
@RequiredArgsConstructor
public class RedisInfoServiceImpl extends ServiceImpl<RedisInfoMapper, RedisInfo> implements RedisInfoService {

    private static final int REQUIRED_NODE_COUNT = 3;
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int DEFAULT_SENTINEL_PORT = 26379;

    private final AgentRegistrationService agentRegistrationService;
    private final ConfigCipherService configCipherService;
    private final ObjectMapper objectMapper;

    /** 查询当前正式纳管节点，并让MASTER稳定排在第一位。 */
    @Override
    public List<RedisInfo> listManaged() {
        return list(new LambdaQueryWrapper<RedisInfo>()
                .eq(RedisInfo::getManagementStatus, "MANAGED")
                .last("ORDER BY CASE WHEN redis_role = 'MASTER' THEN 0 ELSE 1 END, id ASC"));
    }

    /** 确保正式拓扑完整，业务任务不得依赖浏览器保存的拓扑。 */
    @Override
    public List<RedisInfo> requireManagedTopology() {
        List<RedisInfo> managed = listManaged();
        if (managed.size() != REQUIRED_NODE_COUNT) {
            throw new IllegalStateException("redis_info中必须恰好存在3台MANAGED节点，当前为" + managed.size() + "台");
        }
        long masterCount = managed.stream()
                .filter(item -> "MASTER".equals(item.getRedisRole()))
                .count();
        if (masterCount != 1) {
            throw new IllegalStateException("redis_info正式拓扑必须且只能包含1台MASTER");
        }
        return managed;
    }

    /**
     * 首次登记已有拓扑；已有MANAGED记录时拒绝覆盖，避免误改正式环境。
     */
    @Override
    @Transactional
    public List<RedisInfo> initialize(InitializeRedisInfoRequest request) {
        if (!listManaged().isEmpty()) {
            throw new IllegalStateException("正式Redis拓扑已经初始化，不能重复覆盖");
        }

        int redisPort = request.getRedisPort() == null
                ? DEFAULT_REDIS_PORT : request.getRedisPort();
        int sentinelPort = request.getSentinelPort() == null
                ? DEFAULT_SENTINEL_PORT : request.getSentinelPort();
        String passwordCiphertext = configCipherService.encrypt(request.getRedisPassword());
        LocalDateTime now = LocalDateTime.now();
        String masterHost = findMasterHost(request.getNodes());

        for (RedisInfoNodeRequest node : request.getNodes()) {
            String hostIp = node.getHostIp().trim();
            ExecutorInfo executor = agentRegistrationService.requireOnlineExecutorByHostIp(hostIp);
            RedisInfo info = findByExecutorId(executor.getExecutorId());
            if (info == null) {
                info = new RedisInfo();
                info.setExecutorId(executor.getExecutorId());
            }
            applyManagedValues(info, executor.getHostIp(), redisPort, sentinelPort,
                    normalizeRole(node.getRole()), request.getRedisVersion().trim(),
                    masterHost, redisPort, passwordCiphertext, now);
            if (info.getId() == null) {
                save(info);
            } else {
                updateById(info);
            }
        }
        return requireManagedTopology();
    }

    /**
     * 服务器切换成功后提交新拓扑。该方法由任务成功事务调用，失败任务绝不会执行。
     */
    @Override
    public void commitSuccessfulServerSwitch(OperationTask task) {
        List<RedisInfo> oldTopology = requireManagedTopology();
        String passwordCiphertext = oldTopology.get(0).getRedisPasswordCiphertext();
        if (!StringUtils.hasText(passwordCiphertext)) {
            throw new IllegalStateException("当前正式拓扑没有保存Redis密码密文");
        }

        JsonNode params = readTaskParams(task);
        JsonNode nodes = params.path("nodes");
        if (!nodes.isArray() || nodes.size() != REQUIRED_NODE_COUNT) {
            throw new IllegalStateException("服务器切换任务没有保存完整的3台目标服务器");
        }
        String masterHost = requiredText(params, "masterHost");
        String version = requiredText(params, "version");
        int redisPort = params.path("redisPort").asInt(DEFAULT_REDIS_PORT);
        int sentinelPort = params.path("sentinelPort").asInt(DEFAULT_SENTINEL_PORT);
        LocalDateTime now = LocalDateTime.now();

        // 先退出旧拓扑，再写入新拓扑；外层任务成功事务保证两部分同时提交或同时回滚。
        update(new LambdaUpdateWrapper<RedisInfo>()
                .eq(RedisInfo::getManagementStatus, "MANAGED")
                .set(RedisInfo::getManagementStatus, "REMOVED")
                .set(RedisInfo::getRemovedTime, now));

        Set<String> executorIds = new HashSet<>();
        for (JsonNode node : nodes) {
            String executorId = requiredText(node, "executorId");
            if (!executorIds.add(executorId)) {
                throw new IllegalStateException("服务器切换目标执行器重复: " + executorId);
            }
            String hostIp = requiredText(node, "hostIp");
            String role = normalizeRole(requiredText(node, "role"));
            RedisInfo info = findByExecutorId(executorId);
            if (info == null) {
                info = new RedisInfo();
                info.setExecutorId(executorId);
            }
            applyManagedValues(info, hostIp, redisPort, sentinelPort, role, version,
                    masterHost, redisPort, passwordCiphertext, now);
            if (info.getId() == null) {
                save(info);
            } else {
                updateById(info);
            }
        }
        requireManagedTopology();
    }

    /** 找到且只允许存在一台MASTER。 */
    private String findMasterHost(List<RedisInfoNodeRequest> nodes) {
        String masterHost = null;
        for (RedisInfoNodeRequest node : nodes) {
            String role = normalizeRole(node.getRole());
            if ("MASTER".equals(role)) {
                if (masterHost != null) {
                    throw new IllegalArgumentException("正式拓扑只能包含1台MASTER");
                }
                masterHost = node.getHostIp().trim();
            }
        }
        if (masterHost == null) {
            throw new IllegalArgumentException("正式拓扑必须包含1台MASTER");
        }
        return masterHost;
    }

    /** 只接受平台支持的两种Redis角色。 */
    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("Redis角色不能为空");
        }
        String normalized = role.trim().toUpperCase();
        if (!"MASTER".equals(normalized) && !"REPLICA".equals(normalized)) {
            throw new IllegalArgumentException("Redis角色只能是MASTER或REPLICA");
        }
        return normalized;
    }

    /** 给新增记录和重新纳管的历史记录填写一致的正式拓扑字段。 */
    private void applyManagedValues(RedisInfo info, String hostIp, int redisPort,
                                    int sentinelPort, String role, String version,
                                    String masterHost, int masterPort,
                                    String passwordCiphertext, LocalDateTime now) {
        info.setHostIp(hostIp);
        info.setRedisPort(redisPort);
        info.setSentinelPort(sentinelPort);
        info.setRedisRole(role);
        info.setRedisVersion(version);
        info.setMasterHost(masterHost);
        info.setMasterPort(masterPort);
        info.setRedisStatus("UP");
        info.setSentinelStatus("UP");
        info.setRedisPasswordCiphertext(passwordCiphertext);
        info.setManagementStatus("MANAGED");
        info.setLastCheckTime(now);
        info.setManagedTime(now);
        info.setRemovedTime(null);
    }

    /** 按执行器编号读取当前或历史记录。 */
    private RedisInfo findByExecutorId(String executorId) {
        return getOne(new LambdaQueryWrapper<RedisInfo>()
                .eq(RedisInfo::getExecutorId, executorId));
    }

    /** 解析任务创建时保存的服务端可信审计参数。 */
    private JsonNode readTaskParams(OperationTask task) {
        try {
            return objectMapper.readTree(task.getRequestParams());
        } catch (Exception e) {
            throw new IllegalStateException("服务器切换任务参数无法解析", e);
        }
    }

    /** 从JSON中读取不可为空的文本字段。 */
    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("服务器切换任务缺少参数: " + fieldName);
        }
        return value.trim();
    }
}
