package com.redisops.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 平台调用执行器任务接口使用的 HTTP 客户端配置。
 */
@Configuration
public class HttpClientConfig {

    /**
     * 创建带连接和响应超时的 RestTemplate。
     *
     * @return 平台内部 HTTP 客户端
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
