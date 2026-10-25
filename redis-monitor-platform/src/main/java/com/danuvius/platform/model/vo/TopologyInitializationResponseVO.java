package com.danuvius.platform.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 首次拓扑纳管响应。
 */
@Data
@AllArgsConstructor
public class TopologyInitializationResponseVO {

    /** 本次完成首次纳管的三台服务器IP。 */
    private List<String> hostIps;

    /** 首次拓扑纳管结果说明。 */
    private String message;
}
