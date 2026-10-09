package com.redisops.platform.agent.configsnapshot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 执行器基准配置快照的数据访问接口。
 */
@Mapper
public interface ExecutorConfigSnapshotMapper extends BaseMapper<ExecutorConfigSnapshot> {
}
