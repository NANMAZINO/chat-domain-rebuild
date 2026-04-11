package io.github.nanmazino.chatrebuild.chat.controller;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomListResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageHistoryResponse;
import io.github.nanmazino.chatrebuild.chat.service.ChatMessageService;
import io.github.nanmazino.chatrebuild.chat.service.ChatRoomService;
import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
@Validated
@Tag(name = "ChatRoom", description = "채팅방 조회 API")
public class ChatRoomController {

    private static final int DEFAULT_HISTORY_SIZE = 30;

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;

    @GetMapping
    @Operation(summary = "내 채팅방 목록 조회", description = "인증 사용자의 채팅방 목록을 복합 cursor 기반으로 조회합니다.")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getChatRooms(
        @AuthenticationPrincipal JwtPrincipal principal,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime cursorLastMessageAt,
        @RequestParam(required = false) Long cursorRoomId,
        @RequestParam(required = false)
        @Positive(message = "size는 1 이상이어야 합니다.")
        Integer size,
        @RequestParam(required = false) String keyword
    ) {
        ChatRoomListResponse response = chatRoomService.getChatRooms(
            principal.userId(),
            cursorLastMessageAt,
            cursorRoomId,
            size == null ? ChatRoomService.DEFAULT_ROOM_LIST_SIZE : size,
            keyword
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "채팅방 summary 조회", description = "인증 사용자가 참여 중인 채팅방 summary를 조회합니다.")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getChatRoom(
        @PathVariable Long roomId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        ChatRoomDetailResponse response = chatRoomService.getChatRoom(roomId, principal.userId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomId}/messages")
    @Operation(summary = "메시지 내역 조회", description = "인증 사용자가 채팅방 메시지 내역을 cursor 기반으로 조회합니다.")
    public ResponseEntity<ApiResponse<ChatMessageHistoryResponse>> getMessages(
        @PathVariable Long roomId,
        @AuthenticationPrincipal JwtPrincipal principal,
        @RequestParam(required = false) Long cursorMessageId,
        @RequestParam(required = false)
        @Positive(message = "size는 1 이상이어야 합니다.")
        Integer size
    ) {
        ChatMessageHistoryResponse response = chatMessageService.getMessages(
            roomId,
            principal.userId(),
            cursorMessageId,
            size == null ? DEFAULT_HISTORY_SIZE : size
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
