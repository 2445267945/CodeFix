package com.xd.config;


import com.redis.lettucemod.RedisModulesClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RediSearchCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LettuceModConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    /**
     * 重量级客户端，应用生命周期内单例，关闭时自动 shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public RedisModulesClient redisModulesClient() {
        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port);
        if (password != null && !password.isBlank()) {
            builder.withPassword(password);
        }
        return RedisModulesClient.create(builder.build());
    }

    /**
     * 长连接，关闭时自动 close
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> redisModulesConnection(RedisModulesClient redisModulesClient) {
        return redisModulesClient.connect();
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> redisModulesBinaryConnection(RedisModulesClient redisModulesClient) {
        return redisModulesClient.connect(new ByteArrayCodec());
    }

    /**
     * 同步命令对象，从连接中派生，不需要单独关闭
     */
    @Bean
    public RediSearchCommands<String, String> rediSearchCommands(StatefulRedisConnection<String, String> redisModulesConnection) {
        return redisModulesConnection.sync();
    }

    @Bean
    public RediSearchCommands<byte[], byte[]> binaryRediSearchCommands(StatefulRedisConnection<byte[], byte[]> redisModulesBinaryConnection) {
        return redisModulesBinaryConnection.sync();
    }
}