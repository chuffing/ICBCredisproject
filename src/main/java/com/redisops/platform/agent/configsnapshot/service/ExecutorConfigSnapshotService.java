package com.redisops.platform.agent.configsnapshot.service;

import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotStatusData;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotFiles;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotUploadRequest;
import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;

/**
 * 执行器基准配置初始化、校验和查询接口。
 */
public interface ExecutorConfigSnapshotService {

    /** 查询指定执行器是否已经建立基准配置。 */
    ConfigSnapshotStatusData getStatus(String authorization, String executorId);

    /** 首次保存基准配置；已有快照时只校验，绝不覆盖。 */
    ConfigSnapshotStatusData initializeOrVerify(
            String authorization, ConfigSnapshotUploadRequest request);

    /** 获取故障恢复使用的有效平台配置快照。 */
    ExecutorConfigSnapshot requireActiveSnapshot(String executorId);

    /**
     * 解密并重新校验指定执行器的配置正文，供服务器切换时下发给目标 Agent。
     */
    ConfigSnapshotFiles readActiveConfigFiles(String executorId);
}
