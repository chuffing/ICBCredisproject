package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.SentinelNodeInfoMapper;
import com.danuvius.platform.model.dto.agent.SentinelNodeReportDTO;
import com.danuvius.platform.model.dto.monitor.SentinelItemResponse;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.danuvius.platform.util.validate.SentinelNodeReportDTOValidator;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class SentinelNodeInfoServiceImpl extends ServiceImpl<SentinelNodeInfoMapper, SentinelNodeInfo>
        implements SentinelNodeInfoService {

    /** 在线状态 */
    private static final String STATUS_ONLINE = "在线";
    /** 离线状态 */
    private static final String STATUS_OFFLINE = "离线";

    /* 哨兵离线判定阈值：哨兵随指标每 5 分钟上报一次，阈值需大于采集周期 */
    @Value("${platform.sentinel.offline-threshold-seconds:330}")
    private long offlineThresholdSeconds;

    private final SentinelNodeInfoMapper sentinelMapper;
    private final Cache<String, Object> caffeineCache;

    public SentinelNodeInfoServiceImpl(SentinelNodeInfoMapper sentinelMapper,
                                       Cache<String, Object> caffeineCache) {
        this.sentinelMapper = sentinelMapper;
        this.caffeineCache = caffeineCache;
    }

    /**
     * Sentinel 节点列表。
     * 在线状态判定：执行器上报状态为在线，且有心跳、心跳未超时，判为在线；
     * 否则判为离线（执行器明确上报离线时，即使刚刚上报也展示离线）。
     */
    @Override
    public List<SentinelItemResponse> getSentinelList(String managementStatus) {
        String key = CacheKeyUtil.sentinelList(managementStatus);
        Object cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            return (List<SentinelItemResponse>) cached;
        }

        //查询 Sentinel 节点列表
        List<SentinelNodeInfo> list = sentinelMapper.selectList(new LambdaQueryWrapper<SentinelNodeInfo>()
                .eq(SentinelNodeInfo::getManagementStatus, managementStatus)
                .orderByAsc(SentinelNodeInfo::getNodeName)
        );


        LocalDateTime offlineBefore = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);

        List<SentinelItemResponse> result = new ArrayList<>();
        if (!list.isEmpty()) {
            for (SentinelNodeInfo s : list) {
                SentinelItemResponse m = new SentinelItemResponse();
                m.setSentinelId(s.getId());
                m.setSentinelName(s.getNodeName());
                m.setSentinelIp(s.getNodeIp());
                m.setSentinelPort(s.getNodePort());
                // 执行器上报离线，或心跳缺失/超时：离线
                boolean reportedOffline = CommonConstant.OFFLINE.equals(s.getSentinelStatus());
                if (reportedOffline
                        || s.getLastHeartbeatTime() == null
                        || s.getLastHeartbeatTime().isBefore(offlineBefore)) {
                    m.setSentinelStatus(STATUS_OFFLINE);
                } else {
                    m.setSentinelStatus(STATUS_ONLINE);
                }
                m.setLastHeartbeatTime(s.getLastHeartbeatTime());
                result.add(m);
            }
        }else result= Collections.emptyList();

        caffeineCache.put(key, result);
        return result;
    }

    /**
     * 处理随指标一起上报的 Sentinel 节点。
     *
     * <p>哨兵节点由执行器自动发现，平台没有独立的手工纳管入口：
     * 新节点沿用执行器的纳管状态（执行器未纳管时哨兵也不进默认列表），
     * 执行器纳管后下一次上报会把已存在哨兵置为已纳管。
     * 以 nodeIp + nodePort 作为业务唯一键做 upsert。</p>
     */
    @Override
    public int reportNodes(ExecutorInfo executor, List<SentinelNodeReportDTO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        // 手动检测路径不经过 Controller 校验，这里再做一次防御
        SentinelNodeReportDTOValidator.validate(nodes);
        boolean executorManaged = executor != null
                && CommonConstant.MANAGE_ACTIVE.equals(executor.getManagementStatus());

        LocalDateTime now = LocalDateTime.now();
        int handled = 0;
        for (SentinelNodeReportDTO dto : nodes) {
            String ip = dto.getNodeIp().trim();
            String port = dto.getNodePort().trim();

            SentinelNodeInfo exist = sentinelMapper.selectOne(Wrappers.<SentinelNodeInfo>lambdaQuery()
                    .eq(SentinelNodeInfo::getNodeIp, ip)
                    .eq(SentinelNodeInfo::getNodePort, port)
                    .last("LIMIT 1"));

            String nodeName = dto.getNodeName() != null && !dto.getNodeName().trim().isEmpty()
                    ? dto.getNodeName().trim()
                    : "sentinel-" + ip + ":" + port;

            if (exist == null) {
                SentinelNodeInfo node = new SentinelNodeInfo();
                node.setNodeName(nodeName);
                node.setNodeIp(ip);
                node.setNodePort(port);
                node.setSentinelStatus(dto.getSentinelStatus());
                node.setLastHeartbeatTime(now);
                // 新哨兵跟随执行器纳管状态，避免未纳管执行器的哨兵提前出现在前端
                node.setManagementStatus(executorManaged
                        ? CommonConstant.MANAGE_ACTIVE : CommonConstant.MANAGE_INACTIVE);
                node.setCreatedTime(now);
                node.setUpdatedTime(now);
                sentinelMapper.insert(node);
            } else {
                LambdaUpdateWrapper<SentinelNodeInfo> update = new LambdaUpdateWrapper<SentinelNodeInfo>()
                        .set(SentinelNodeInfo::getNodeName, nodeName)
                        .set(SentinelNodeInfo::getSentinelStatus, dto.getSentinelStatus())
                        .set(SentinelNodeInfo::getLastHeartbeatTime, now)
                        .set(SentinelNodeInfo::getUpdatedTime, now)
                        .eq(SentinelNodeInfo::getId, exist.getId());
                if (executorManaged) {
                    update.set(SentinelNodeInfo::getManagementStatus, CommonConstant.MANAGE_ACTIVE);
                }
                sentinelMapper.update(null, update);
            }
            handled++;
        }

        // Sentinel 列表与拓扑缓存失效
        caffeineCache.invalidate(CacheKeyUtil.sentinelList(CommonConstant.MANAGE_ACTIVE));
        caffeineCache.invalidate(CacheKeyUtil.sentinelList(CommonConstant.MANAGE_INACTIVE));
        log.info("执行器 {} 随指标上报 Sentinel 节点 {} 个", executor == null ? "-" : executor.getId(), handled);
        return handled;
    }
}
