package com.danuvius.platform.model.vo;

import lombok.Data;

/**
 * 执行器接收运维任务后的响应。
 */
@Data
public class AgentTaskSubmitResponseVO {

    /** 执行器已经接收的运维任务编号。 */
    private String taskNo;

}
