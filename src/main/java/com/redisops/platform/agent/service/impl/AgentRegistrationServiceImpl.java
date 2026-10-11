package com.redisops.platform.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.redisops.platform.agent.config.AgentPlatformProperties;
import com.redisops.platform.agent.model.HeartbeatRequest;
import com.redisops.platform.agent.model.RegisterData;
import com.redisops.platform.agent.model.RegisterRequest;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.mapper.ExecutorInfoMapper;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.agent.type.ExecutorManagementStatus;
import com.redisops.platform.agent.type.ExecutorOnlineStatus;
import com.redisops.platform.security.service.TokenCipherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 处理执行器注册、认证、心跳和离线判定。
 */
@Service
public class AgentRegistrationServiceImpl implements AgentRegistrationService {

    private static final int DEFAULT_AGENT_PORT = 9101;

    private final ExecutorInfoMapper executorInfoMapper;
    private final AgentPlatformProperties properties;
    private final TokenCipherService tokenCipherService;

    /** 注入执行器数据访问和认证组件。 */
    public AgentRegistrationServiceImpl(ExecutorInfoMapper executorInfoMapper,
                                        AgentPlatformProperties properties,
                                        TokenCipherService tokenCipherService) {
        this.executorInfoMapper = executorInfoMapper;
        this.properties = properties;
        this.tokenCipherService = tokenCipherService;
    }

    /**
     * 注册新执行器，或者刷新同名执行器的地址和 Token。
     *
     * @param request 执行器注册信息
     * @return 平台分配的 agentId 和原始 Token
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterData register(RegisterRequest request) {
        validateRegisterRequest(request);
        if (!safeEquals(properties.getRegisterCode(), request.getRegisterCode())) {
            throw new SecurityException("注册码错误");
        }

        ExecutorInfo executor = findByExecutorName(request.getAgentName());
        LocalDateTime now = LocalDateTime.now();
        String token = createToken();

        if (executor == null) {
            executor = new ExecutorInfo();
            executor.setExecutorId(createAgentId());
            executor.setExecutorName(request.getAgentName().trim());
            executor.setRegisterTime(now);
            executor.setManagementStatus("ACTIVE");
        }

        // 同名执行器重新注册时保留 executorId，只更新其当前网络信息和认证令牌。
        executor.setHostname(request.getHostname().trim());
        executor.setHostIp(request.getIp().trim());
        executor.setExecutorPort(request.getAgentPort() == null
                ? DEFAULT_AGENT_PORT : request.getAgentPort());
        executor.setOsName(request.getOs());
        executor.setExecutorStatus(ExecutorOnlineStatus.ONLINE.name());
        executor.setLastHeartbeatTime(now);
        executor.setTokenCiphertext(tokenCipherService.encrypt(token));

        if (executor.getId() == null) {
            executorInfoMapper.insert(executor);
        } else {
            executorInfoMapper.updateById(executor);
        }
        return new RegisterData(executor.getExecutorId(), token);
    }

    /**
     * 校验心跳身份并更新执行器最后心跳时间。
     *
     * @param authorization HTTP Authorization 请求头
     * @param request 心跳内容
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(String authorization, HeartbeatRequest request) {
        if (request == null || !StringUtils.hasText(request.getAgentId())) {
            throw new IllegalArgumentException("agentId 不能为空");
        }

        String headerToken = parseBearerToken(authorization);
        if (!safeEquals(headerToken, request.getToken())) {
            throw new SecurityException("请求头 Token 与请求体 Token 不一致");
        }

        ExecutorInfo executor = findByExecutorId(request.getAgentId());
        if (executor == null || !"ACTIVE".equals(executor.getManagementStatus())) {
            throw new SecurityException("执行器不存在或已停用");
        }

        String storedToken = tokenCipherService.decrypt(executor.getTokenCiphertext());
        if (!safeEquals(storedToken, headerToken)) {
            throw new SecurityException("Token 无效");
        }

        LambdaUpdateWrapper<ExecutorInfo> update = new LambdaUpdateWrapper<>();
        update.eq(ExecutorInfo::getExecutorId, request.getAgentId())
                .set(ExecutorInfo::getExecutorStatus, "ONLINE")
                .set(ExecutorInfo::getLastHeartbeatTime, LocalDateTime.now());
        if (StringUtils.hasText(request.getHostname())) {
            update.set(ExecutorInfo::getHostname, request.getHostname().trim());
        }
        if (StringUtils.hasText(request.getIp())) {
            update.set(ExecutorInfo::getHostIp, request.getIp().trim());
        }
        executorInfoMapper.update(null, update);
    }

    /**
     * 把超过 60 秒未上报心跳的在线执行器改为离线。
     *
     * @return 本次改为离线的记录数
     */
    @Override
    public int markTimedOutExecutorsOffline() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusSeconds(properties.getOfflineTimeoutSeconds());
        LambdaUpdateWrapper<ExecutorInfo> update = new LambdaUpdateWrapper<>();
        update.eq(ExecutorInfo::getExecutorStatus, "ONLINE")
                .lt(ExecutorInfo::getLastHeartbeatTime, threshold)
                .set(ExecutorInfo::getExecutorStatus, "OFFLINE");
        return executorInfoMapper.update(null, update);
    }

    /** 获取当前所有执行器，供开发阶段检查注册结果。 */
    @Override
    public List<ExecutorInfo> listExecutors() {
        return executorInfoMapper.selectList(
                new LambdaQueryWrapper<ExecutorInfo>().orderByAsc(ExecutorInfo::getExecutorName));
    }

    /**
     * 读取一台可以接收任务的在线执行器。
     *
     * @param executorId 平台执行器编号
     * @return 在线且启用的执行器
     */
    @Override
    public ExecutorInfo requireOnlineExecutor(String executorId) {
        if (!StringUtils.hasText(executorId)) {
            throw new IllegalArgumentException("executorId 不能为空");
        }
        // 根据执行器编号查询对应执行器。
        ExecutorInfo executor = findByExecutorId(executorId.trim());
        if (executor == null) {
            throw new IllegalArgumentException("执行器不存在: " + executorId);
        }
        if (!ExecutorManagementStatus.ACTIVE.name().equals(executor.getManagementStatus())) {
            throw new IllegalStateException("执行器已停用: " + executorId);
        }
        if (!ExecutorOnlineStatus.ONLINE.name().equals(executor.getExecutorStatus())) {
            throw new IllegalStateException("执行器当前离线: " + executorId);
        }
        return executor;
    }

    /**
     * 根据服务器IP找到唯一的在线执行器。
     * 服务器切换页面只提交用户输入的IP，执行器编号必须由平台数据库解析，不能由前端伪造。
     */
    @Override
    public ExecutorInfo requireOnlineExecutorByHostIp(String hostIp) {
        if (!StringUtils.hasText(hostIp)) {
            throw new IllegalArgumentException("服务器IP不能为空");
        }
        List<ExecutorInfo> matches = executorInfoMapper.selectList(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getHostIp, hostIp.trim())
                        .eq(ExecutorInfo::getManagementStatus, "ACTIVE"));
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("没有找到该服务器IP对应的已注册执行器: " + hostIp);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("服务器IP对应多个启用执行器，请先清理重复注册: " + hostIp);
        }
        ExecutorInfo executor = matches.get(0);
        if (!"ONLINE".equals(executor.getExecutorStatus())) {
            throw new IllegalStateException("服务器IP对应的执行器当前离线: " + hostIp);
        }
        return executor;
    }

    /**
     * 解密向指定执行器下发任务时需要的 Token。
     *
     * @param executor 已从数据库读取的执行器
     * @return 原始 Token
     */
    @Override
    public String readDispatchToken(ExecutorInfo executor) {
        if (executor == null || !StringUtils.hasText(executor.getTokenCiphertext())) {
            throw new IllegalStateException("执行器 Token 不存在");
        }
        return tokenCipherService.decrypt(executor.getTokenCiphertext());
    }

    /**
     * 校验执行器上报任务结果时携带的 Bearer Token。
     *
     * @param executorId 执行器编号
     * @param authorization Authorization 请求头
     * @return 已通过认证的执行器
     */
    @Override
    public ExecutorInfo authenticateExecutor(String executorId, String authorization) {
        String token = parseBearerToken(authorization);
        ExecutorInfo executor = findByExecutorId(executorId);
        if (executor == null || !"ACTIVE".equals(executor.getManagementStatus())) {
            throw new SecurityException("执行器不存在或已停用");
        }
        String storedToken = tokenCipherService.decrypt(executor.getTokenCiphertext());
        if (!safeEquals(storedToken, token)) {
            throw new SecurityException("Token 无效");
        }
        return executor;
    }

    /**
     * 记录执行器最近一次上报运维结果的时间。
     *
     * @param executorId 执行器编号
     */
    @Override
    public void touchLastReportTime(String executorId) {
        LambdaUpdateWrapper<ExecutorInfo> update = new LambdaUpdateWrapper<>();
        update.eq(ExecutorInfo::getExecutorId, executorId)
                .set(ExecutorInfo::getLastReportTime, LocalDateTime.now());
        executorInfoMapper.update(null, update);
    }

    /** 根据执行器名称查询记录。 */
    private ExecutorInfo findByExecutorName(String executorName) {
        return executorInfoMapper.selectOne(new LambdaQueryWrapper<ExecutorInfo>()
                .eq(ExecutorInfo::getExecutorName, executorName.trim()));
    }

    /** 根据平台执行器编号查询记录。 */
    private ExecutorInfo findByExecutorId(String executorId) {
        // 创建一个用于查询ExecutorInfo数据的条件构造器。
        // MP只能借助Getter方法来定位属性。
        return executorInfoMapper.selectOne(new LambdaQueryWrapper<ExecutorInfo>()
                .eq(ExecutorInfo::getExecutorId, executorId));
    }

    /** 校验注册接口必填参数和端口范围。 */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getAgentName())
                || !StringUtils.hasText(request.getHostname())
                || !StringUtils.hasText(request.getIp())) {
            throw new IllegalArgumentException("agentName、hostname 和 ip 不能为空");
        }
        if (request.getAgentPort() != null
                && (request.getAgentPort() < 1 || request.getAgentPort() > 65535)) {
            throw new IllegalArgumentException("agentPort 必须在 1 到 65535 之间");
        }
    }

    /** 从 Authorization 请求头中提取 Bearer Token。 */
    private String parseBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("缺少 Bearer Token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new SecurityException("Bearer Token 不能为空");
        }
        return token;
    }

    /** 生成平台内部稳定使用的执行器编号。 */
    private String createAgentId() {
        return "AGENT-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase();
    }

    /** 生成只返回一次给执行器的随机认证 Token。 */
    private String createToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /** 使用固定耗时比较敏感字符串，避免直接比较 Token。 */
    private boolean safeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
