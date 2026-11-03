package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.metric.MetricReportItemDTO;

import static com.danuvius.platform.common.exception.validator.NumberValidator.requiredDoubleBetween;
import static com.danuvius.platform.common.exception.validator.NumberValidator.requiredIntegerBetween;

public class MetricReportItemDTOValidator {
    public static void validate(MetricReportItemDTO req) {
        IdValidator.checkUUID(req.getToken(), "token");

        requiredDoubleBetween(req.getCpuPer(), 0, 100, "cpuPer");
        requiredDoubleBetween(req.getMemServerPer(), 0, 100, "memServerPer");
        requiredDoubleBetween(req.getMemUs(), 0, 1024, "memUs");          // GB，防御上限
        requiredDoubleBetween(req.getLatency(), 0, 10_000_000, "latency"); // 微秒，防御上限 10s
        requiredDoubleBetween(req.getOps(), 0, 100_000_000, "ops");        // 次/秒


        // 可选指标：有值才校验范围
        StringValidator.notNull(req.getIoIik(), "ioIik");
        NumberValidator.parseDoubleNonNegative(req.getIoIik(), "ioIik");
        StringValidator.notNull(req.getIoIok(), "ioIok");
        NumberValidator.parseDoubleNonNegative(req.getIoIok(), "ioIok");

        // 执行器 ping 平台延迟：可空，有值非负
        if (req.getPingLatency() != null && !req.getPingLatency().trim().isEmpty()) {
            NumberValidator.parseDoubleNonNegative(req.getPingLatency(), "pingLatency");
        }

        // 节点角色
        StringValidator.isEnumValue(req.getRole(), CommonConstant.ROLE_TYPES, "role");

        // 检验非空+格式，版本号 / runId
        StringValidator.matches(req.getRedisVersion(), CommonConstant.VERSION_PATTERN, "redisVersion");

        StringValidator.matches(req.getRunId(), CommonConstant.RUN_ID_PATTERN, "runId");


        StringValidator.matches(req.getMasterHost(), CommonConstant.HOST_PATTERN, "masterHost");
        StringValidator.maxLength(req.getMasterHost(), 255, "masterHost");

        requiredIntegerBetween(req.getMasterPort(), 1, 65535, "masterPort");
        requiredDoubleBetween(req.getReplDelay(), 0, 86400, "replDelay");

        // 成功场景下 errorMsg 一般为空，若存在也限制长度
        StringValidator.notNull(req.getErrorMsg(), "errorMsg");
        StringValidator.maxLength(req.getErrorMsg(), 2000, "errorMsg");


    }
}
