package io.github.nanmazino.chatrebuild.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageHistoryResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.exception.ChatMemberNotFoundException;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidChatMessageCursorException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChatMessageServiceTest extends IntegrationTestSupport {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("ACTIVE 멤버가 메시지를 보내면 저장 결과와 브로드캐스트 응답 형태를 반환한다")
    void sendMessageStoresMessageForActiveMember() {
        User author = userRepository.save(new User("author@test.com", "pw", "author"));
        User sender = userRepository.save(new User("sender@test.com", "pw", "sender"));
        Post post = postRepository.save(new Post(author, "title", "content", 4, PostStatus.OPEN));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        chatRoomMemberRepository.save(new ChatRoomMember(room, author, ChatRoomMemberStatus.ACTIVE, post.getCreatedAt()));
        room.increaseMemberCount();
        chatRoomMemberRepository.save(new ChatRoomMember(room, sender, ChatRoomMemberStatus.ACTIVE, post.getCreatedAt()));
        room.increaseMemberCount();
        chatRoomRepository.saveAndFlush(room);

        ChatMessageResponse response = chatMessageService.sendMessage(
            room.getId(),
            sender.getId(),
            new ChatSendRequest("안녕하세요", ChatMessageType.TEXT)
        );

        ChatMessage savedMessage = chatMessageRepository.findById(response.messageId())
            .orElseThrow(() -> new AssertionError("저장된 메시지가 있어야 합니다."));
        ChatRoom refreshedRoom = chatRoomRepository.findById(room.getId())
            .orElseThrow(() -> new AssertionError("채팅방이 유지되어야 합니다."));

        assertThat(chatMessageRepository.countByRoomId(room.getId())).isEqualTo(1);
        assertThat(response.roomId()).isEqualTo(room.getId());
        assertThat(response.sender().userId()).isEqualTo(sender.getId());
        assertThat(response.sender().nickname()).isEqualTo(sender.getNickname());
        assertThat(response.content()).isEqualTo("안녕하세요");
        assertThat(response.type()).isEqualTo(ChatMessageType.TEXT);
        assertThat(response.createdAt()).isNotNull();
        assertThat(savedMessage.getRoom().getId()).isEqualTo(room.getId());
        assertThat(savedMessage.getSender().getId()).isEqualTo(sender.getId());
        assertThat(savedMessage.getContent()).isEqualTo("안녕하세요");
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(savedMessage.getCreatedAt()).isNotNull();
        assertThat(refreshedRoom.getLastMessageId()).isEqualTo(savedMessage.getId());
        assertThat(refreshedRoom.getLastMessagePreview()).isEqualTo("안녕하세요");
        assertThat(refreshedRoom.getLastMessageAt()).isEqualTo(savedMessage.getCreatedAt());
    }

    @Test
    @DisplayName("메시지 저장 시 lastMessagePreview는 255자로 잘라 저장한다")
    void sendMessageTruncatesSummaryPreviewToColumnLength() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);
        String longContent = "a".repeat(300);

        ChatMessageResponse response = chatMessageService.sendMessage(
            fixture.room().getId(),
            fixture.member().getId(),
            new ChatSendRequest(longContent, ChatMessageType.TEXT)
        );

        ChatRoom refreshedRoom = chatRoomRepository.findById(fixture.room().getId())
            .orElseThrow(() -> new AssertionError("채팅방이 유지되어야 합니다."));

        assertThat(response.content()).isEqualTo(longContent);
        assertThat(refreshedRoom.getLastMessageId()).isEqualTo(response.messageId());
        assertThat(refreshedRoom.getLastMessagePreview()).hasSize(255);
        assertThat(refreshedRoom.getLastMessagePreview()).isEqualTo(longContent.substring(0, 255));
    }

    @Test
    @DisplayName("cursor 없이 조회하면 최신 메시지부터 size 기준으로 반환하고 다음 cursor를 계산한다")
    void getMessagesReturnsLatestMessagesWithoutCursor() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);
        ChatMessage first = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.author(),
            "첫 번째",
            ChatMessageType.TEXT
        ));
        ChatMessage second = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.member(),
            "두 번째",
            ChatMessageType.TEXT
        ));
        ChatMessage third = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.author(),
            "세 번째",
            ChatMessageType.TEXT
        ));

        ChatMessageHistoryResponse response = chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            null,
            2
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).messageId()).isEqualTo(third.getId());
        assertThat(response.items().get(1).messageId()).isEqualTo(second.getId());
        assertThat(response.nextCursor()).isEqualTo(second.getId());
        assertThat(response.hasNext()).isTrue();
        assertThat(response.items()).extracting(ChatMessageResponse::content)
            .containsExactly("세 번째", "두 번째");
        assertThat(first.getId()).isLessThan(second.getId());
    }

    @Test
    @DisplayName("cursor 기준으로 다음 페이지를 조회하고 마지막 페이지에서는 nextCursor가 없다")
    void getMessagesReturnsNextPageByCursor() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);
        ChatMessage first = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.author(),
            "첫 번째",
            ChatMessageType.TEXT
        ));
        ChatMessage second = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.member(),
            "두 번째",
            ChatMessageType.TEXT
        ));
        ChatMessage third = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.author(),
            "세 번째",
            ChatMessageType.TEXT
        ));

        ChatMessageHistoryResponse firstPage = chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            null,
            2
        );
        ChatMessageHistoryResponse nextPage = chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            firstPage.nextCursor(),
            2
        );

        assertThat(firstPage.nextCursor()).isEqualTo(second.getId());
        assertThat(nextPage.items()).hasSize(1);
        assertThat(nextPage.items().get(0).messageId()).isEqualTo(first.getId());
        assertThat(nextPage.hasNext()).isFalse();
        assertThat(nextPage.nextCursor()).isNull();
        assertThat(third.getId()).isGreaterThan(second.getId());
    }

    @Test
    @DisplayName("가장 오래된 메시지 cursor는 빈 마지막 페이지로 처리한다")
    void getMessagesReturnsEmptyLastPageWhenCursorIsOldestMessage() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);
        ChatMessage first = chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.author(),
            "첫 번째",
            ChatMessageType.TEXT
        ));
        chatMessageRepository.save(new ChatMessage(
            fixture.room(),
            fixture.member(),
            "두 번째",
            ChatMessageType.TEXT
        ));

        ChatMessageHistoryResponse response = chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            first.getId(),
            2
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("0 이하 cursor는 잘못된 메시지 cursor 예외를 던진다")
    void getMessagesRejectsNonPositiveCursor() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);

        assertThatThrownBy(() -> chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            0L,
            10
        )).isInstanceOf(InvalidChatMessageCursorException.class);

        assertThatThrownBy(() -> chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            -1L,
            10
        )).isInstanceOf(InvalidChatMessageCursorException.class);
    }

    @Test
    @DisplayName("존재하지 않는 cursor는 잘못된 메시지 cursor 예외를 던진다")
    void getMessagesRejectsNonExistentCursor() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);

        assertThatThrownBy(() -> chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            999999L,
            10
        )).isInstanceOf(InvalidChatMessageCursorException.class);
    }

    @Test
    @DisplayName("다른 방 메시지 cursor는 잘못된 메시지 cursor 예외를 던진다")
    void getMessagesRejectsCursorFromAnotherRoom() {
        MessageFixture fixture = createMessageFixture("main", PostStatus.OPEN);
        MessageFixture otherFixture = createMessageFixture("other", PostStatus.OPEN);
        ChatMessage otherRoomMessage = chatMessageRepository.save(new ChatMessage(
            otherFixture.room(),
            otherFixture.author(),
            "다른 방 메시지",
            ChatMessageType.TEXT
        ));

        assertThatThrownBy(() -> chatMessageService.getMessages(
            fixture.room().getId(),
            fixture.member().getId(),
            otherRoomMessage.getId(),
            10
        )).isInstanceOf(InvalidChatMessageCursorException.class);
    }

    @Test
    @DisplayName("게시글이 CLOSED 또는 DELETED여도 ACTIVE 멤버는 히스토리를 조회할 수 있다")
    void getMessagesAllowsActiveMemberForClosedOrDeletedPost() {
        MessageFixture closedFixture = createMessageFixture(PostStatus.CLOSED);
        chatMessageRepository.save(new ChatMessage(closedFixture.room(), closedFixture.author(), "닫힘", ChatMessageType.TEXT));

        ChatMessageHistoryResponse closedResponse = chatMessageService.getMessages(
            closedFixture.room().getId(),
            closedFixture.member().getId(),
            null,
            10
        );

        MessageFixture deletedFixture = createMessageFixture(PostStatus.DELETED);
        chatMessageRepository.save(new ChatMessage(deletedFixture.room(), deletedFixture.author(), "삭제됨", ChatMessageType.TEXT));

        ChatMessageHistoryResponse deletedResponse = chatMessageService.getMessages(
            deletedFixture.room().getId(),
            deletedFixture.member().getId(),
            null,
            10
        );

        assertThat(closedResponse.items()).hasSize(1);
        assertThat(closedResponse.items().get(0).content()).isEqualTo("닫힘");
        assertThat(deletedResponse.items()).hasSize(1);
        assertThat(deletedResponse.items().get(0).content()).isEqualTo("삭제됨");
    }

    @Test
    @DisplayName("ACTIVE 멤버가 아니면 메시지 히스토리 조회에 실패한다")
    void getMessagesFailsForNonActiveMember() {
        MessageFixture fixture = createMessageFixture(PostStatus.OPEN);
        User outsider = userRepository.save(new User("outsider@test.com", "pw", "outsider"));

        assertThatThrownBy(() -> chatMessageService.getMessages(
            fixture.room().getId(),
            outsider.getId(),
            null,
            10
        )).isInstanceOf(ChatMemberNotFoundException.class);
    }

    private MessageFixture createMessageFixture(PostStatus status) {
        return createMessageFixture(status.name().toLowerCase(), status);
    }

    private MessageFixture createMessageFixture(String key, PostStatus status) {
        User author = userRepository.save(new User("author-" + key + "@test.com", "pw", "author-" + key));
        User member = userRepository.save(new User("member-" + key + "@test.com", "pw", "member-" + key));
        Post post = postRepository.save(new Post(author, "title-" + status, "content", 4, status));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        chatRoomMemberRepository.save(new ChatRoomMember(room, author, ChatRoomMemberStatus.ACTIVE, post.getCreatedAt()));
        room.increaseMemberCount();
        chatRoomMemberRepository.save(new ChatRoomMember(room, member, ChatRoomMemberStatus.ACTIVE, post.getCreatedAt()));
        room.increaseMemberCount();
        chatRoomRepository.saveAndFlush(room);

        return new MessageFixture(author, member, room);
    }

    private record MessageFixture(
        User author,
        User member,
        ChatRoom room
    ) {
    }
}
