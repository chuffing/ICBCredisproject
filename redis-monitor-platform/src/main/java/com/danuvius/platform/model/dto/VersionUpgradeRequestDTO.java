package com.danuvius.platform.model.dto;

import lombok.Data;

/**
 * Redis版本升级请求。
 */
@Data
public class VersionUpgradeRequestDTO {

    /** 需要切换到的安装包记录编号。 */
    private Long packageId;

    /** 本次版本升级的操作原因。 */
    private String operationReason;

    /** 本次版本升级的操作人。 */
    private String createdBy;
}
