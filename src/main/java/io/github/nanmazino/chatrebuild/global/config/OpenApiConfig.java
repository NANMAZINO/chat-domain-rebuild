package io.github.nanmazino.chatrebuild.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Chat Rebuild API",
        version = "v1",
        description = "게시글 기반 그룹 채팅 백엔드 API"
    )
)
public class OpenApiConfig {

}
