package com.redisops.platform.redisinfo.service;

import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import com.redisops.platform.redisinfo.model.InitializeRedisInfoRequest;

import java.util.List;

/**
 * 正式纳管Redis拓扑的查询、初始化和切换提交接口。
 */
public interface RedisInfoService {

    /** 查询当前正式纳管的三台Redis实例，不返回密码密文。 */
    List<RedisInfo> listManaged();

    /** 读取并校验当前正式纳管拓扑必须恰好包含三台实例。 */
    List<RedisInfo> requireManagedTopology();

    /** 首次登记公司已有的正式Redis拓扑。 */
    List<RedisInfo> initialize(InitializeRedisInfoRequest request);

    /** 在服务器切换全部步骤成功后原子提交新的正式纳管拓扑。 */
    void commitSuccessfulServerSwitch(OperationTask task);
}
