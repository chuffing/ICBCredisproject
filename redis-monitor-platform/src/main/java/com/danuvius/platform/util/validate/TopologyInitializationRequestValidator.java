package com.danuvius.platform.util.validate;

import cn.hutool.core.lang.Validator;
import com.danuvius.platform.common.constant.TopologyInitializationConstants;
import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.TopologyInitializationRequestDTO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 首次拓扑纳管请求参数校验。
 */
public final class TopologyInitializationRequestValidator {

    private TopologyInitializationRequestValidator() {
    }

    /**
     * 校验并规范化首次拓扑纳管请求。
     */
    public static void validate(
            TopologyInitializationRequestDTO request) {
        if (request == null) {
            throw new ParameterException(
                    ParameterCode.PARAM_NULL,
                    "首次拓扑纳管请求不能为空"
            );
        }

        // 1. 前端必须选择三台服务器。
        List<String> requestHostIps = request.getHostIps();
        ListValidator.notEmpty(requestHostIps, "hostIps");
        if (requestHostIps.size()
                != TopologyInitializationConstants.REQUIRED_NODE_COUNT) {
            throw new ParameterException(
                    ParameterCode.PARAM_COLLECTION_SIZE_ERROR,
                    "字段[hostIps]必须包含三台服务器IP"
            );
        }

        // 2. 校验IP格式和重复情况。
        List<String> normalizedHostIps = new ArrayList<>();
        Set<String> uniqueHostIps = new HashSet<>();
        for (String value : requestHostIps) {
            String hostIp = StringUtils.trimToNull(value);
            StringValidator.notBlank(hostIp, "hostIps");
            if (!Validator.isIpv4(hostIp)) {
                throw new ParameterException(
                        ParameterCode.PARAM_FORMAT_ERROR,
                        "字段[hostIps]IP格式不正确"
                );
            }
            if (!uniqueHostIps.add(hostIp)) {
                throw new ParameterException(
                        ParameterCode.PARAM_DUPLICATE,
                        "三台服务器IP不能重复"
                );
            }
            normalizedHostIps.add(hostIp);
        }

        // 3. 写回规范化后的服务器IP。
        request.setHostIps(normalizedHostIps);
    }
}
