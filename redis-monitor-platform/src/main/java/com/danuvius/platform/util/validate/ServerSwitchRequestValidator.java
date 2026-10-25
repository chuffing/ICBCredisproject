package com.danuvius.platform.util.validate;

import cn.hutool.core.lang.Validator;
import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.ServerSwitchRequestDTO;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis服务器切换请求参数校验。
 */
public final class ServerSwitchRequestValidator {

    private ServerSwitchRequestValidator() {
    }

    /**
     * 校验并规范化服务器切换请求。
     */
    public static void validate(ServerSwitchRequestDTO request) {
        if (request == null) {
            throw new ParameterException(
                    ParameterCode.PARAM_NULL,
                    "服务器切换请求不能为空"
            );
        }

        // 1. 校验目标Redis安装包编号。
        NumberValidator.notNull(request.getPackageId(), "packageId");
        NumberValidator.positive(request.getPackageId(), "packageId");

        // 2. 校验目标主节点服务器IP。
        String masterHostIp = StringUtils.trimToNull(
                request.getMasterHostIp()
        );
        validateHostIp(masterHostIp, "masterHostIp");

        // 3. 校验两个目标从节点服务器IP。
        List<String> replicaHostIps = request.getReplicaHostIps();
        ListValidator.notEmpty(replicaHostIps, "replicaHostIps");
        if (replicaHostIps.size()
                != ServerSwitchConstants.REPLICA_NODE_COUNT) {
            throw new ParameterException(
                    ParameterCode.PARAM_COLLECTION_SIZE_ERROR,
                    "字段[replicaHostIps]必须包含两个服务器IP"
            );
        }

        Set<String> targetHostIps = new HashSet<>();
        targetHostIps.add(masterHostIp);
        for (int index = 0; index < replicaHostIps.size(); index++) {
            String hostIp = StringUtils.trimToNull(
                    replicaHostIps.get(index)
            );
            validateHostIp(hostIp, "replicaHostIps");
            if (!targetHostIps.add(hostIp)) {
                throw new ParameterException(
                        ParameterCode.PARAM_DUPLICATE,
                        "三台目标服务器IP不能重复"
                );
            }
            replicaHostIps.set(index, hostIp);
        }

        // 4. 校验任务公共参数，并写回规范化后的值。
        request.setMasterHostIp(masterHostIp);
        request.setOperationReason(
                OperationCommonValidator.validateReason(
                        request.getOperationReason(),
                        "operationReason"
                )
        );
        request.setCreatedBy(
                OperationCommonValidator.validateOperator(
                        request.getCreatedBy(),
                        "createdBy"
                )
        );
    }

    /**
     * 校验服务器IPv4地址。
     */
    private static void validateHostIp(
            String hostIp,
            String fieldName) {
        StringValidator.notBlank(hostIp, fieldName);
        if (!Validator.isIpv4(hostIp)) {
            throw new ParameterException(
                    ParameterCode.PARAM_FORMAT_ERROR,
                    "字段[" + fieldName + "]IP格式不正确"
            );
        }
    }
}
