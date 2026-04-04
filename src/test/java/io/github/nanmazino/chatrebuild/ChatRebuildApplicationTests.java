package io.github.nanmazino.chatrebuild;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChatRebuildApplicationTests extends IntegrationTestSupport {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
    }

    @Test
    void redisConnectionLoads() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
