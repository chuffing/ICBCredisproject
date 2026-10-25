package com.danuvius.platform.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 首次拓扑纳管请求。
 */
@Data
public class TopologyInitializationRequestDTO {

    /** 三台待纳管服务器IP，列表顺序没有业务含义。 */
    private List<String> hostIps;
}
