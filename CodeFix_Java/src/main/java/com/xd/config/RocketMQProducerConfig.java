package com.xd.config;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RocketMQProducerConfig {

    @Value("${mq.rocketmq.name-server}")
    private String nameSrvAddr;


    @Value("${mq.rocketmq.producer.groupName}")
    private String producerGroup;

    @Bean
    public DefaultMQProducer getRocketMQProducer(){
        if (StringUtils.isEmpty(producerGroup)){
            throw new RuntimeException("producerGroup is null !!!");
        }
        if (StringUtils.isEmpty(nameSrvAddr)){
            throw new RuntimeException("namesrvAddr is null !!!");
        }
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameSrvAddr);
        producer.setRetryTimesWhenSendFailed(2);
        try {
            producer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        }
        return producer;
    }
}
