package com.danuvius.platform.config;

import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 运维任务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.operation-task")
public class OperationTaskProperties {

    /** 未单独配置操作类型时使用的默认结果等待时间，秒。 */
    private long defaultResultTimeoutSeconds = 180;

    /** 各执行器操作类型对应的结果等待时间，秒。 */
    private Map<ExecutorOperationTypeEnum, Long> resultTimeoutSeconds =
            new EnumMap<>(ExecutorOperationTypeEnum.class);

    /**
     * 获取指定操作类型的结果等待时间。
     *
     * @param operationType 执行器操作类型
     * @return 结果等待时间，秒
     */
    public long resolveResultTimeoutSeconds(String operationType) {
        try {
            ExecutorOperationTypeEnum type =
                    ExecutorOperationTypeEnum.valueOf(operationType);
            Long timeoutSeconds = resultTimeoutSeconds.get(type);
            if (timeoutSeconds != null && timeoutSeconds > 0) {
                return timeoutSeconds;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // 未识别的操作类型使用默认等待时间，避免扫描任务异常中断。
        }
        return defaultResultTimeoutSeconds;
    }
}
