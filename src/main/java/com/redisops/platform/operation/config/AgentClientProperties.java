package com.redisops.platform.operation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 平台访问执行器的协议和接口路径；IP、端口由执行器注册记录提供。 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "platform.agent-client")
public class AgentClientProperties {

    /** 执行器实际支持的协议：http 或 https。 */
    private String scheme = "http";

    /** 普通 JSON 运维任务接口，路径以 / 开头。 */
    private String taskPath = "/api/agent/task";

    /** 执行器任务结果查询接口，路径以 / 开头。 */
    private String taskResultPath = "/api/agent/task-result";

    /** 版本包下发接口，路径以 / 开头。 */
    private String versionTaskPath = "/api/agent/version-task";

    /** 配置文件下发接口，路径以 / 开头。 */
    private String configTaskPath = "/api/agent/config-task";
}
