package io.github.nanmazino.chatrebuild.chat.controller;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatReadRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatReadResponse;
import io.github.nanmazino.chatrebuild.chat.service.ChatReadService;
import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
@Validated
@Tag(name = "ChatRead", description = "채팅 읽음 처리 API")
public class ChatReadController {

    private final ChatReadService chatReadService;

    @PatchMapping("/{roomId}/read")
    @Operation(summary = "채팅방 읽음 처리", description = "인증 사용자의 마지막 읽은 메시지 정보를 갱신합니다.")
    public ResponseEntity<ApiResponse<ChatReadResponse>> markAsRead(
        @PathVariable Long roomId,
        @AuthenticationPrincipal JwtPrincipal principal,
        @Valid @RequestBody ChatReadRequest request
    ) {
        ChatReadResponse response = chatReadService.markAsRead(roomId, principal.userId(), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
