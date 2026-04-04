package io.github.nanmazino.chatrebuild.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;
import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.response.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        writeErrorResponse(response, ErrorCode.AUTH_FORBIDDEN);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
            response.getWriter(),
            ApiResponse.failure(ErrorResponse.from(errorCode, errorCode.getMessage()))
        );
    }
}
