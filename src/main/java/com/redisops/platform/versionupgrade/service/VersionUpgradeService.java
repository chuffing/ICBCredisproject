package com.redisops.platform.versionupgrade.service;

import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.versionupgrade.model.VersionUpgradeTaskRequest;

/**
 * 三节点 Redis + Sentinel 版本升级业务接口。
 */
public interface VersionUpgradeService {

    /** 校验版本包和三台节点，创建完整升级计划并开始调度。 */
    OperationTaskDetail createAndDispatch(VersionUpgradeTaskRequest request);
}
