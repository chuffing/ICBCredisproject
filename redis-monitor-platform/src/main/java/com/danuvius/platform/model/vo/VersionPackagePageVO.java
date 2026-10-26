package com.danuvius.platform.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Redis版本安装包分页结果。
 */
@Data
public class VersionPackagePageVO {

    /** 当前页安装包。 */
    private List<VersionPackageItemVO> records;

    /** 安装包总数。 */
    private Long total;

    /** 当前页码。 */
    private Long current;

    /** 每页数量。 */
    private Long size;

    /** 总页数。 */
    private Long pages;
}
