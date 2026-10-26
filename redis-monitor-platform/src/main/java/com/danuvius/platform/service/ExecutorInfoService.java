package com.danuvius.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danuvius.platform.model.dto.agent.AgentRegisterResultDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;

import java.util.List;
import java.util.Map;

public interface ExecutorInfoService extends IService<ExecutorInfo> {

    /**
     * 注册分两阶段，根据redisReady判断
     * redisReady=false / null：只注册执行器，不检查 redisInfo、不建实例
     * redisReady=true 且 redisInfo 非空：
     *      一阶段注册：未绑定实例-》注册执行器+建实例绑定
     *      二阶段注册：未绑定实例-》建实例绑定
     *      重复调用注册：已绑定实例-》直接返回已就绪
     * redisReady=true 但 redisInfo 为空：不绑定，返回未就绪
     * @param registerCode
     * @param hostIp
     * @param executorPort
     * @param redisInfo
     * @return
     */
    AgentRegisterResultDTO register(String registerCode, String hostIp, String executorPort,
                                    Map<String, String> redisInfo,
                                    Boolean redisReady);

    /**
     * 心跳：只更新执行器心跳时间与状态，不处理 Redis 绑定
     * @param executorToken
     * @param hostIp
     * @param executorPort
     * @return
     */
    Boolean heartbeat(String executorToken, String hostIp, String executorPort);


    /**
     * 批量变更执行器纳管状态，并同步其绑定 Redis 实例的纳管状态。
     * 只有纳管状态为 1 的执行器/实例才会出现在前端默认实例列表、拓扑和报告中。
     *
     * @param executorIds      执行器表主键 id 列表（executor_info.id，雪花 id）
     * @param managementStatus 纳管状态：0=未纳管 / 1=已纳管
     * @return 实际更新的执行器行数
     */
    int updateManagementStatus(List<String> executorIds, String managementStatus);
}