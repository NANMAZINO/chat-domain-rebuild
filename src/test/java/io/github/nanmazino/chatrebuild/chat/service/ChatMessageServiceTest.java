package io.github.nanmazino.chatrebuild.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
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
    }
}
