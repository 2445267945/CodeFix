package com.xd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        // 1. 创建工厂并设置超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 连接超时：5秒（指Java跟Python服务建立TCP连接的时间）
        factory.setConnectTimeout(5000);

        // 读取超时：120秒（指等待Python返回数据的时间，因为AI推理慢，宁可多等也不要超时）
        factory.setReadTimeout(180000);

        // 2. 创建 RestTemplate 并放入 Spring 容器
        return new RestTemplate(factory);
    }
}