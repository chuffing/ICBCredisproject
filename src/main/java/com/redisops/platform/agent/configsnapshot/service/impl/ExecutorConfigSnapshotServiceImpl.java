package com.redisops.platform.agent.configsnapshot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotStatusData;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotFiles;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotUploadRequest;
import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.configsnapshot.mapper.ExecutorConfigSnapshotMapper;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.security.service.ConfigCipherService;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 保存平台基准配置，并阻止重新注册静默覆盖已有配置。
 */
@Service
public class ExecutorConfigSnapshotServiceImpl implements ExecutorConfigSnapshotService {

    private static final int MAX_CONFIG_BYTES = 10 * 1024 * 1024;

    private final ExecutorConfigSnapshotMapper snapshotMapper;
    private final AgentRegistrationService agentRegistrationService;
    private final ConfigCipherService configCipherService;

    /** 注入配置快照、执行器认证和配置加密组件。 */
    public ExecutorConfigSnapshotServiceImpl(
            ExecutorConfigSnapshotMapper snapshotMapper,
            AgentRegistrationService agentRegistrationService,
            ConfigCipherService configCipherService) {
        this.snapshotMapper = snapshotMapper;
        this.agentRegistrationService = agentRegistrationService;
        this.configCipherService = configCipherService;
    }

    /** 查询平台是否已经保存指定执行器的基准配置。 */
    @Override
    public ConfigSnapshotStatusData getStatus(String authorization, String executorId) {
        if (!StringUtils.hasText(executorId)) {
            throw new IllegalArgumentException("executorId 不能为空");
        }
        String normalizedExecutorId = executorId.trim();
        agentRegistrationService.authenticateExecutor(normalizedExecutorId, authorization);
        ExecutorConfigSnapshot snapshot = findByExecutorId(normalizedExecutorId);
        return toStatus(snapshot);
    }

    /**
     * 首次上传时建立基准；已有记录时只比较摘要并刷新校验时间。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized ConfigSnapshotStatusData initializeOrVerify(
            String authorization, ConfigSnapshotUploadRequest request) {
        validateUploadRequest(request);
        String executorId = request.getAgentId().trim();
        agentRegistrationService.authenticateExecutor(executorId, authorization);

        byte[] redisConfig = decodeConfig(
                request.getRedisConfigBase64(), "redis.conf");
        byte[] sentinelConfig = decodeConfig(
                request.getSentinelConfigBase64(), "sentinel.conf");
        String redisSha256 = calculateSha256(redisConfig);
        String sentinelSha256 = calculateSha256(sentinelConfig);

        ExecutorConfigSnapshot existing = findByExecutorId(executorId);
        if (existing != null) {
            verifyExistingSnapshot(existing, redisSha256, sentinelSha256);
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<ExecutorConfigSnapshot> update = new LambdaUpdateWrapper<>();
            update.eq(ExecutorConfigSnapshot::getId, existing.getId())
                    .set(ExecutorConfigSnapshot::getLastVerifiedTime, now);
            snapshotMapper.update(null, update);
            existing.setLastVerifiedTime(now);
            return toStatus(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        ExecutorConfigSnapshot snapshot = new ExecutorConfigSnapshot();
        snapshot.setExecutorId(executorId);
        // 加密Base64正文可以完整保留配置文件的原始字节和换行格式。
        snapshot.setRedisConfigCiphertext(
                configCipherService.encrypt(request.getRedisConfigBase64().trim()));
        snapshot.setRedisConfigSha256(redisSha256);
        snapshot.setSentinelConfigCiphertext(
                configCipherService.encrypt(request.getSentinelConfigBase64().trim()));
        snapshot.setSentinelConfigSha256(sentinelSha256);
        snapshot.setSnapshotStatus("ACTIVE");
        snapshot.setInitializedTime(now);
        snapshot.setLastVerifiedTime(now);
        snapshotMapper.insert(snapshot);
        return toStatus(snapshot);
    }

    /** 读取故障恢复所需的有效配置快照。 */
    @Override
    public ExecutorConfigSnapshot requireActiveSnapshot(String executorId) {
        if (!StringUtils.hasText(executorId)) {
            throw new IllegalArgumentException("executorId 不能为空");
        }
        ExecutorConfigSnapshot snapshot = findByExecutorId(executorId.trim());
        if (snapshot == null) {
            throw new IllegalStateException("执行器尚未建立平台基准配置: " + executorId);
        }
        if (!"ACTIVE".equals(snapshot.getSnapshotStatus())) {
            throw new IllegalStateException("执行器基准配置当前不可用: " + executorId);
        }
        return snapshot;
    }

    /**
     * 解密平台保存的两份配置，并重新计算摘要防止密文或数据库摘要被意外修改。
     */
    @Override
    public ConfigSnapshotFiles readActiveConfigFiles(String executorId) {
        ExecutorConfigSnapshot snapshot = requireActiveSnapshot(executorId);
        byte[] redisConfig = decodeStoredConfig(
                snapshot.getRedisConfigCiphertext(), "redis.conf");
        byte[] sentinelConfig = decodeStoredConfig(
                snapshot.getSentinelConfigCiphertext(), "sentinel.conf");

        String actualRedisSha256 = calculateSha256(redisConfig);
        String actualSentinelSha256 = calculateSha256(sentinelConfig);
        if (!snapshot.getRedisConfigSha256().equals(actualRedisSha256)) {
            throw new IllegalStateException("平台Redis配置正文与保存的摘要不一致: "
                    + executorId);
        }
        if (!snapshot.getSentinelConfigSha256().equals(actualSentinelSha256)) {
            throw new IllegalStateException("平台Sentinel配置正文与保存的摘要不一致: "
                    + executorId);
        }
        return new ConfigSnapshotFiles(
                redisConfig, actualRedisSha256,
                sentinelConfig, actualSentinelSha256);
    }

    /** 解密数据库密文，再把其中保存的 Base64 正文还原成原始配置字节。 */
    private byte[] decodeStoredConfig(String ciphertext, String fileName) {
        if (!StringUtils.hasText(ciphertext)) {
            throw new IllegalStateException("平台缺少配置密文: " + fileName);
        }
        try {
            String base64 = configCipherService.decrypt(ciphertext);
            return decodeConfig(base64, fileName);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("平台配置正文无法解码: " + fileName, e);
        }
    }

    /** 校验上传请求中的必填字段。 */
    private void validateUploadRequest(ConfigSnapshotUploadRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getAgentId())
                || !StringUtils.hasText(request.getRedisConfigBase64())
                || !StringUtils.hasText(request.getSentinelConfigBase64())) {
            throw new IllegalArgumentException(
                    "agentId、redisConfigBase64 和 sentinelConfigBase64 不能为空");
        }
    }

    /** 解码并限制单个配置文件最大为10MiB。 */
    private byte[] decodeConfig(String base64, String fileName) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            if (bytes.length == 0) {
                throw new IllegalArgumentException(fileName + " 不能为空文件");
            }
            if (bytes.length > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException(fileName + " 不能超过10MiB");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("不能为空文件")
                    || e.getMessage().contains("不能超过10MiB"))) {
                throw e;
            }
            throw new IllegalArgumentException(fileName + " 不是合法Base64内容", e);
        }
    }

    /** 计算配置原始字节的SHA-256。 */
    private String calculateSha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前JVM不支持SHA-256", e);
        }
    }

    /** 已有快照只允许校验，摘要不同就拒绝覆盖。 */
    private void verifyExistingSnapshot(ExecutorConfigSnapshot existing,
                                        String redisSha256,
                                        String sentinelSha256) {
        if (!existing.getRedisConfigSha256().equals(redisSha256)) {
            throw new IllegalStateException(
                    "本机Redis备份配置与平台基准配置不一致，禁止覆盖平台快照");
        }
        if (!existing.getSentinelConfigSha256().equals(sentinelSha256)) {
            throw new IllegalStateException(
                    "本机Sentinel备份配置与平台基准配置不一致，禁止覆盖平台快照");
        }
    }

    /** 根据执行器编号查询当前基准配置。 */
    private ExecutorConfigSnapshot findByExecutorId(String executorId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ExecutorConfigSnapshot>()
                .eq(ExecutorConfigSnapshot::getExecutorId, executorId));
    }

    /** 把数据库快照转换为不包含配置正文的安全响应。 */
    private ConfigSnapshotStatusData toStatus(ExecutorConfigSnapshot snapshot) {
        if (snapshot == null) {
            return new ConfigSnapshotStatusData(false, null, null);
        }
        return new ConfigSnapshotStatusData(
                true,
                snapshot.getRedisConfigSha256(),
                snapshot.getSentinelConfigSha256());
    }
}
