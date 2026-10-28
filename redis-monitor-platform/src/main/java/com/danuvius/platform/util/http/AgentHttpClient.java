package com.danuvius.platform.util.http;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.danuvius.platform.model.dto.metric.MetricReportItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 执行器（Agent）HTTP 调用统一封装。
 * 所有对执行器的调用都通过此类发出，统一超时、统一异常、统一响应解析。
 */
@Slf4j
@Component
public class AgentHttpClient {

    /** 采集接口路径，如执行器端有变化，改此常量即可 */
    private static final String COLLECT_PATH = "/executor/collect";

    /** 请求超时（毫秒） */
    private static final int TIMEOUT_MS = 15000;

    /**
     * 触发执行器立即执行一次 Redis 数据采集。
     *
     * @param hostIp 执行器 IP
     * @param port   执行器 HTTP 端口
     * @return 统一响应；data 为采集到的指标列表。失败时 code = -1。
     */
    public AgentCollectResponse<List<MetricReportItemDTO>> triggerCollect(String hostIp, String port) {
        AgentCollectResponse<List<MetricReportItemDTO>> result = new AgentCollectResponse<>();

        if (hostIp == null || hostIp.isEmpty() || port == null || port.isEmpty()) {
            result.setCode(-1);
            result.setMessage("执行器地址为空");
            return result;
        }

        String url = "http://" + hostIp + ":" + port + COLLECT_PATH;
        try {
            String respStr = HttpUtil.post(url, "{}", TIMEOUT_MS);
            if (respStr == null || respStr.trim().isEmpty()) {
                result.setCode(-1);
                result.setMessage("执行器返回为空");
                return result;
            }
            AgentCollectResponse<List<MetricReportItemDTO>> parsed = JSON.parseObject(
                    respStr,
                    new TypeReference<AgentCollectResponse<List<MetricReportItemDTO>>>() {
                    });
            if (parsed == null) {
                result.setCode(-1);
                result.setMessage("执行器返回格式解析失败");
                return result;
            }
            return parsed;
        } catch (Exception e) {
            log.error("调用执行器采集接口失败: url={}", url, e);
            result.setCode(-1);
            result.setMessage("调用执行器失败: " + e.getMessage());
            return result;
        }
    }
}