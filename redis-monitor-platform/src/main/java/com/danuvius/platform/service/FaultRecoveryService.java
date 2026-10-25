package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.FaultRecoveryRequestDTO;
import com.danuvius.platform.model.vo.FaultRecoveryResponseVO;

/**
 * 故障恢复服务。
 */
public interface FaultRecoveryService {

    /**
     * 对当前纳管的Redis环境执行故障恢复。
     *
     * @param request 故障恢复请求
     * @return 故障恢复结果
     */
    FaultRecoveryResponseVO faultRecovery(
            FaultRecoveryRequestDTO request);

    /**
     * 查询故障恢复任务状态。
     *
     * @param taskNo 故障恢复任务编号
     * @return 故障恢复任务状态
     */
    FaultRecoveryResponseVO getFaultRecoveryStatus(String taskNo);
}
