package com.xd.config;

import com.xd.exception.BusinessException;
import com.xd.mq.AgentMQListener;
import com.xd.mq.HeartbeatMQListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Configuration
public class RocketMQConsumerConfig {
    // 配置项大家自定义即可
    @Value("${mq.rocketmq.name-server}")
    private String nameSrvAddr;

    @Value("${mq.rocketmq.consumer.groupName}")
    private String agentConsumerGroup;

    @Value("#{'${mq.rocketmq.consumer.topics}'.split(',')}")
    private List<String> agentTopicList;

    @Autowired
    private AgentMQListener AgentMessageListener;

    @Value("${mq.rocketmq.heartbeat-consumer.groupName}")
    private String heartbeatConsumerGroup;

    @Value("#{'${mq.rocketmq.heartbeat-consumer.topics}'.split(',')}")
    private List<String> heartbeatTopicList;

    @Autowired
    private HeartbeatMQListener heartbeatMessageListener;

    @Bean
    public DefaultMQPushConsumer getAgentRocketMQConsumer() throws BusinessException {

        if (StringUtils.isEmpty(agentConsumerGroup)) {
            throw new RuntimeException("consumerGroup is null !!!");
        }
        if (StringUtils.isEmpty(nameSrvAddr)) {
            throw new RuntimeException("namesrvAddr is null !!!");
        }
        if (StringUtils.isEmpty(agentTopicList)) {
            throw new RuntimeException("topics is null !!!");
        }
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(agentConsumerGroup);
        consumer.setNamesrvAddr(nameSrvAddr);
        consumer.registerMessageListener(AgentMessageListener);
        /**
         * 设置Consumer第一次启动是从队列头部开始消费还是队列尾部开始消费
         * 如果非第一次启动，那么按照上次消费的位置继续消费
         */
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        /**
         * 设置消费模型，集群还是广播，默认为集群
         */
        consumer.setMessageModel(MessageModel.CLUSTERING);
        /**
         * 设置一次消费消息的条数，默认为1条
         */
        consumer.setConsumeMessageBatchMaxSize(1);
        log.info(
                "RocketMQ Consumer started: group={}, instanceName={}, topics={}, nameSrv={}",
                agentConsumerGroup,
                consumer.getInstanceName(),
                agentTopicList,
                nameSrvAddr
        );
        try {
            /**
             * 设置该消费者订阅的主题和tag，如果是订阅该主题下的所有tag，则tag使用*；如果需要指定订阅该主题下的某些tag，则使用||分割，例如tag1||tag2||tag3
             */
            agentTopicList.forEach(topic -> {
                try {
                    consumer.subscribe(topic, "*");
                } catch (MQClientException e) {
                    throw new RuntimeException(e);
                }
            });
            consumer.start();
            log.info("consumer is start !!! groupName:{},topics:{},namesrvAddr:{}", agentConsumerGroup, agentTopicList, nameSrvAddr);
        } catch (MQClientException e) {
            log.error("consumer is start !!! groupName:{},topics:{},namesrvAddr:{}", agentConsumerGroup, agentTopicList, nameSrvAddr, e);
            throw new RuntimeException(e);
        }
        return consumer;
    }


    @Bean
    public DefaultMQPushConsumer getHeartbeatRocketMQConsumer() throws RuntimeException {
        if (StringUtils.isEmpty(heartbeatConsumerGroup)) {
            throw new RuntimeException("heartbeatConsumerGroup is null !!!");
        }
        if (StringUtils.isEmpty(nameSrvAddr)) {
            throw new RuntimeException("namesrvAddr is null !!!");
        }
        if (StringUtils.isEmpty(heartbeatTopicList)) {
            throw new RuntimeException("heartbeat topics is null !!!");
        }

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(heartbeatConsumerGroup);
        consumer.setNamesrvAddr(nameSrvAddr);
        consumer.registerMessageListener(heartbeatMessageListener);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(1);
        log.info(
                "RocketMQ Heartbeat Consumer started: group={}, topics={}, nameSrv={}",
                heartbeatConsumerGroup,
                heartbeatTopicList,
                nameSrvAddr
        );
        try {
            heartbeatTopicList.forEach(topic -> {
                try {
                    consumer.subscribe(topic, "*");
                } catch (MQClientException e) {
                    throw new RuntimeException(e);
                }
            });
            consumer.start();
        } catch (MQClientException e) {
            log.error(
                    "RocketMQ Heartbeat Consumer start failed: group={}, topics={}, nameSrv={}",
                    heartbeatConsumerGroup,
                    heartbeatTopicList,
                    nameSrvAddr,
                    e
            );
            throw new RuntimeException(e);
        }

        return consumer;
    }

}
