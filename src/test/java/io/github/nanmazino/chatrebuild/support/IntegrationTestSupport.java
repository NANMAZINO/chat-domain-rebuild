package io.github.nanmazino.chatrebuild.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class IntegrationTestSupport {

    protected static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("chat_rebuild")
        .withUsername("chat_user")
        .withPassword("chat_password");

    protected static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379);

    static {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }
}
