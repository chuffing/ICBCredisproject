package com.redisops.platform.agent.service;

import com.redisops.platform.agent.model.HeartbeatRequest;
import com.redisops.platform.agent.model.RegisterData;
import com.redisops.platform.agent.model.RegisterRequest;
import com.redisops.platform.agent.entity.ExecutorInfo;

import java.util.List;

/**
 * 执行器注册、心跳、认证和连接信息查询接口。
 */
public interface AgentRegistrationService {

    /** 注册或重新注册执行器。 */
    RegisterData register(RegisterRequest request);

    /** 校验并保存执行器心跳。 */
    void heartbeat(String authorization, HeartbeatRequest request);

    /** 将心跳超时的在线执行器标记为离线。 */
    int markTimedOutExecutorsOffline();

    /** 查询全部执行器。 */
    List<ExecutorInfo> listExecutors();

    /** 查询一台能够接收运维任务的在线执行器。 */
    ExecutorInfo requireOnlineExecutor(String executorId);

    /** 根据用户输入的服务器IP查询一台能够接收运维任务的在线执行器。 */
    ExecutorInfo requireOnlineExecutorByHostIp(String hostIp);

    /** 解密下发任务所需的执行器 Token。 */
    String readDispatchToken(ExecutorInfo executor);

    /** 校验执行器结果回调的身份。 */
    ExecutorInfo authenticateExecutor(String executorId, String authorization);

    /** 更新执行器最后一次结果上报时间。 */
    void touchLastReportTime(String executorId);
}
