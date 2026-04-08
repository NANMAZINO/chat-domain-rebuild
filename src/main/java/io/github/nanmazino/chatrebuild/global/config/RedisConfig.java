package io.github.nanmazino.chatrebuild.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.pubsub.ChatPubSubService;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ChatRoomDetailResponse> chatRoomSummaryRedisTemplate(
        RedisConnectionFactory redisConnectionFactory,
        ObjectMapper objectMapper
    ) {
        return createRedisTemplate(redisConnectionFactory, objectMapper, ChatRoomDetailResponse.class);
    }

    private <T> RedisTemplate<String, T> createRedisTemplate(
        RedisConnectionFactory redisConnectionFactory,
        ObjectMapper objectMapper,
        Class<T> valueType
    ) {
        RedisTemplate<String, T> redisTemplate = new RedisTemplate<>();
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<T> valueSerializer =
            new Jackson2JsonRedisSerializer<>(objectMapper.copy(), valueType);

        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashKeySerializer(keySerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
        RedisConnectionFactory redisConnectionFactory,
        ChatPubSubService chatPubSubService
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        StringRedisSerializer serializer = new StringRedisSerializer();

        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
            (message, pattern) -> {
                String payload = serializer.deserialize(message.getBody());

                if (payload != null) {
                    chatPubSubService.handlePublishedMessage(payload);
                }
            },
            new ChannelTopic(ChatPubSubService.CHANNEL_NAME)
        );

        return container;
    }
}
