package com.danuvius.platform.model.dto.executor;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 执行器纳管状态变更请求
 * 执行器注册时默认未纳管（management_status=0），平台侧在“执行器列表”中手动选择执行器后调用该接口批量纳管/取消纳管
 */
@Data
public class ExecutorManagementRequest implements Serializable {

    /** 执行器表主键 id 列表（executor_info.id，雪花 id） */
    private List<String> executorIds;

    /** 纳管状态：0=未纳管 / 1=已纳管 */
    private String managementStatus;
}
