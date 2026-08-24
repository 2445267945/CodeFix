package com.xd.mq;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class RocketMQTopicInitializer {

    @Value("${mq.rocketmq.name-server}")
    private String nameSrvAddr;

    @Value("${mq.rocketmq.topic.status}")
    private String consumerTopics;
    @Value("${mq.rocketmq.topic.statusQueueNum}")
    private Integer statusQueueNum;

    /**
     * 开发环境：
     * 启动 Spring Boot 时确保 Agent Status Topic 存在并且是 FIFO。
     *
     * 注意：
     * 已经存在的 NORMAL Topic 不会被这里“自动转换”为 FIFO。
     * 当前开发环境建议先手动删除旧 Topic，再由这里创建。
     */
    @PostConstruct
    public void init() {
        String statusTopic = consumerTopics.trim();
        if (statusTopic.isEmpty()) {
            throw new IllegalStateException("RocketMQ consumer topics 不能为空");
        }
        DefaultMQAdminExt admin = new DefaultMQAdminExt();
        try {
            admin.setNamesrvAddr(nameSrvAddr);
            admin.setInstanceName("topic_initializer_" + System.currentTimeMillis());
            admin.start();
            Map<String, String> attributes = new HashMap<>();
            /*
             * RocketMQ 5.x FIFO Topic：
             *
             * +message.type=FIFO
             */
            attributes.put("+message.type", "FIFO");
            /*
             * 创建 8 个 Queue。
             *
             * 你目前 Topic 已经是 8 Queue，
             * 这里保持一致。
             *
             * key 使用自动创建 Topic Key。
             */
            admin.createTopic(TopicValidator.AUTO_CREATE_TOPIC_KEY_TOPIC, statusTopic, statusQueueNum, attributes);
            log.info("RocketMQ FIFO Topic 初始化成功: topic={}, nameServer={}, queueNum={}", statusTopic, nameSrvAddr, 8);
        } catch (MQClientException e) {
            log.warn("RocketMQ Topic 初始化失败: topic={}, message={}", statusTopic, e.getMessage(), e);
        } finally {
            admin.shutdown();
        }
    }
}
