package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.agent.SentinelNodeReportDTO;

import java.util.List;

/**
 * 随指标上报的 Sentinel 节点列表校验。
 */
public class SentinelNodeReportDTOValidator {

    /** 单次最多上报的哨兵节点数，防御异常报文 */
    private static final int MAX_NODE_SIZE = 100;

    /**
     * @param nodes 哨兵节点列表，可为 null/空（表示本次未采集到哨兵，不影响指标上报）
     */
    public static void validate(List<SentinelNodeReportDTO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        if (nodes.size() > MAX_NODE_SIZE) {
            throw new ParameterException(ParameterCode.PARAM_RANGE_ERROR,
                    "字段[sentinels]数量不能超过" + MAX_NODE_SIZE);
        }
        for (SentinelNodeReportDTO node : nodes) {
            if (node == null) {
                throw new ParameterException(ParameterCode.PARAM_NULL, "字段[sentinels]不能包含空元素");
            }
            StringValidator.notBlank(node.getNodeIp(), "nodeIp");
            StringValidator.matches(node.getNodeIp(), CommonConstant.HOST_PATTERN, "nodeIp");
            // sentinel_node_info.node_ip varchar(20)
            StringValidator.maxLength(node.getNodeIp(), 20, "nodeIp");
            StringValidator.notBlank(node.getNodePort(), "nodePort");
            StringValidator.numeric(node.getNodePort(), "nodePort");
            int port;
            try {
                port = Integer.parseInt(node.getNodePort());
            } catch (NumberFormatException e) {
                throw new ParameterException(ParameterCode.PARAM_RANGE_ERROR,
                        "字段[nodePort]必须在1~65535之间");
            }
            if (port < 1 || port > 65535) {
                throw new ParameterException(ParameterCode.PARAM_RANGE_ERROR,
                        "字段[nodePort]必须在1~65535之间");
            }
            StringValidator.isEnumValue(node.getSentinelStatus(), CommonConstant.ENABLE_STATUS, "sentinelStatus");
            if (node.getNodeName() != null) {
                StringValidator.maxLength(node.getNodeName(), 64, "nodeName");
            }
            if (node.getMasterName() != null) {
                StringValidator.maxLength(node.getMasterName(), 64, "masterName");
            }
        }
    }
}
