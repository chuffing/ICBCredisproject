package com.redisops.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证完整 Spring 依赖装配及 HTTP 启动，不执行数据库初始化和定时运维任务。 */
@SpringBootTest(classes = RedisPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.sql.init.mode=never",
                "spring.main.allow-circular-references=false",
                "server.address=127.0.0.1"
        })
class PlatformStartupTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // 阻止离线检查和结果核对任务操作真实数据库或执行器。
    @MockBean(name = "operationTaskScheduler")
    private TaskScheduler scheduler;

    @Test
    void shouldStartWithoutCircularDependenciesAndServeHttp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/test", String.class);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("redis platform is running", response.getBody());
    }
}
