package com.redisops.platform.operation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证外部部署配置能绑定到执行器客户端。 */
class AgentClientPropertiesTest {

    @Test
    void shouldBindConfiguredProtocolAndAllEndpointPaths() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(AgentClientProperties.class)
                .withPropertyValues(
                        "platform.agent-client.scheme=https",
                        "platform.agent-client.task-path=/internal/task",
                        "platform.agent-client.task-result-path=/internal/result",
                        "platform.agent-client.version-task-path=/internal/version",
                        "platform.agent-client.config-task-path=/internal/config")
                .run(context -> {
                    AgentClientProperties properties = context.getBean(AgentClientProperties.class);
                    assertEquals("https", properties.getScheme());
                    assertEquals("/internal/task", properties.getTaskPath());
                    assertEquals("/internal/result", properties.getTaskResultPath());
                    assertEquals("/internal/version", properties.getVersionTaskPath());
                    assertEquals("/internal/config", properties.getConfigTaskPath());
                });
    }
}
