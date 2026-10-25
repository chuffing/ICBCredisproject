package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;
import com.danuvius.platform.model.vo.VersionUpgradeResponseVO;

/**
 * Redis版本升级服务。
 */
public interface VersionUpgradeService {

    VersionUpgradeResponseVO upgrade(VersionUpgradeRequestDTO request);

    VersionUpgradeResponseVO getUpgradeStatus(String taskNo);
}
