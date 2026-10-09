package com.redisops.platform.agent.status.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.agent.status.entity.ExecutorRuntimeStatus;
import com.redisops.platform.agent.status.mapper.ExecutorRuntimeStatusMapper;
import com.redisops.platform.agent.status.model.MetricReportRequest;
import com.redisops.platform.agent.status.service.ExecutorRuntimeStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 保存每台执行器最近一次Redis和Sentinel采集结果。 */
@Service
public class ExecutorRuntimeStatusServiceImpl implements ExecutorRuntimeStatusService {

    private final ExecutorRuntimeStatusMapper statusMapper;
    private final AgentRegistrationService agentRegistrationService;

    /** 注入状态数据访问和执行器认证服务。 */
    public ExecutorRuntimeStatusServiceImpl(ExecutorRuntimeStatusMapper statusMapper,
                                            AgentRegistrationService agentRegistrationService) {
        this.statusMapper = statusMapper;
        this.agentRegistrationService = agentRegistrationService;
    }

    /** 验证上报身份，并以执行器编号为键覆盖保存最新状态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(String authorization, MetricReportRequest request) {
        validate(request);
        String executorId = request.getAgentId().trim();
        agentRegistrationService.authenticateExecutor(executorId, authorization);

        MetricReportRequest.MetricPayload metric = request.getMetric();
        LocalDateTime now = LocalDateTime.now();
        ExecutorRuntimeStatus existing = findByExecutorId(executorId);
        if (existing == null) {
            ExecutorRuntimeStatus status = new ExecutorRuntimeStatus();
            status.setExecutorId(executorId);
            copyMetric(status, metric, now);
            statusMapper.insert(status);
        } else {
            ExecutorRuntimeStatus values = new ExecutorRuntimeStatus();
            copyMetric(values, metric, now);
            LambdaUpdateWrapper<ExecutorRuntimeStatus> update = new LambdaUpdateWrapper<>();
            update.eq(ExecutorRuntimeStatus::getExecutorId, executorId)
                    .set(ExecutorRuntimeStatus::getRedisStatus, values.getRedisStatus())
                    .set(ExecutorRuntimeStatus::getRedisRole, values.getRedisRole())
                    .set(ExecutorRuntimeStatus::getRedisVersion, values.getRedisVersion())
                    .set(ExecutorRuntimeStatus::getRedisMasterHost, values.getRedisMasterHost())
                    .set(ExecutorRuntimeStatus::getRedisMasterPort, values.getRedisMasterPort())
                    .set(ExecutorRuntimeStatus::getRedisErrorMessage, values.getRedisErrorMessage())
                    .set(ExecutorRuntimeStatus::getSentinelStatus, values.getSentinelStatus())
                    .set(ExecutorRuntimeStatus::getSentinelMasterName, values.getSentinelMasterName())
                    .set(ExecutorRuntimeStatus::getSentinelMasterHost, values.getSentinelMasterHost())
                    .set(ExecutorRuntimeStatus::getSentinelMasterPort, values.getSentinelMasterPort())
                    .set(ExecutorRuntimeStatus::getSentinelErrorMessage, values.getSentinelErrorMessage())
                    .set(ExecutorRuntimeStatus::getCollectTime, values.getCollectTime())
                    .set(ExecutorRuntimeStatus::getReceivedTime, now);
            statusMapper.update(null, update);
        }
        // last_report_time 表示平台最近收到该执行器任意状态或任务结果的时间。
        agentRegistrationService.touchLastReportTime(executorId);
    }

    /** 按执行器编号返回最新状态，供页面与执行器列表关联。 */
    @Override
    public List<ExecutorRuntimeStatus> listLatest() {
        return statusMapper.selectList(new LambdaQueryWrapper<ExecutorRuntimeStatus>()
                .orderByAsc(ExecutorRuntimeStatus::getExecutorId));
    }

    /** 把执行器上报字段转换成平台最新状态实体。 */
    private void copyMetric(ExecutorRuntimeStatus target,
                            MetricReportRequest.MetricPayload metric,
                            LocalDateTime receivedTime) {
        target.setRedisStatus(normalizeStatus(metric.getStatus()));
        target.setRedisRole(normalizeRole(metric.getRole()));
        target.setRedisVersion(trimToNull(metric.getRedisVersion()));
        target.setRedisMasterHost(trimToNull(metric.getMasterHost()));
        target.setRedisMasterPort(parsePort(metric.getMasterPort()));
        target.setRedisErrorMessage(trimToNull(metric.getErrorMsg()));
        target.setSentinelStatus(normalizeStatus(metric.getSentinelStatus()));
        target.setSentinelMasterName(trimToNull(metric.getSentinelMasterName()));
        target.setSentinelMasterHost(trimToNull(metric.getSentinelMasterHost()));
        target.setSentinelMasterPort(metric.getSentinelMasterPort());
        target.setSentinelErrorMessage(trimToNull(metric.getSentinelErrorMsg()));
        target.setCollectTime(metric.getCollectTime());
        target.setReceivedTime(receivedTime);
    }

    /** 检查上报必须包含执行器编号、采集对象和采集时间。 */
    private void validate(MetricReportRequest request) {
        if (request == null || !StringUtils.hasText(request.getAgentId())) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (request.getMetric() == null) {
            throw new IllegalArgumentException("metric 不能为空");
        }
        if (request.getMetric().getCollectTime() == null) {
            throw new IllegalArgumentException("collectTime 不能为空");
        }
    }

    /** 根据执行器编号读取现有最新状态。 */
    private ExecutorRuntimeStatus findByExecutorId(String executorId) {
        return statusMapper.selectOne(new LambdaQueryWrapper<ExecutorRuntimeStatus>()
                .eq(ExecutorRuntimeStatus::getExecutorId, executorId));
    }

    /** 将up/down状态统一成平台使用的大写值。 */
    private String normalizeStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "UNKNOWN";
    }

    /** 将Redis的slave旧术语统一成页面使用的REPLICA。 */
    private String normalizeRole(String value) {
        if (!StringUtils.hasText(value)) return null;
        String role = value.trim().toUpperCase();
        return "SLAVE".equals(role) ? "REPLICA" : role;
    }

    /** 安全解析执行器使用字符串上报的端口。 */
    private Integer parsePort(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65535 ? port : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 去除首尾空白，并把空字符串转换为null。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}