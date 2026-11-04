package com.danuvius.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.MetricLogMapper;
import com.danuvius.platform.model.entity.MetricLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 监控采集数据定期清理
 * 每天凌晨 3 点清理超过保留天数（platform.retain-days，默认 7 天）的采集数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricDataCleanupTask {

    private final MetricLogMapper metricLogMapper;

    /** 采集数据保留天数 */
    @Value("${platform.retain-days:7}")
    private int retainDays;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredData() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retainDays);

        int rows = metricLogMapper.delete(new LambdaQueryWrapper<MetricLog>()
                .lt(MetricLog::getTm, threshold));
        if (rows > 0) {
            log.info("采集数据表rms_metric_log定期清理结果：{}", CommonConstant.SUCCESS);
        } else {
            log.info("采集数据表rms_metric_log定期清理结果：{}", CommonConstant.FAILURE);
        }
    }
}
