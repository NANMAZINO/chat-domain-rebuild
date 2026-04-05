package io.github.nanmazino.chatrebuild.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.exception.ChatRoomFullException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChatMembershipServiceTest extends IntegrationTestSupport {

    private static final AtomicLong TEST_USER_SEQUENCE = new AtomicLong();

    @Autowired
    private ChatMembershipService chatMembershipService;

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
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동시에 여러 사용자가 참여해도 정원을 넘지 않고 memberCount가 ACTIVE 수와 일치한다")
    void joinPostMaintainsCapacityAndMemberCountUnderConcurrency() throws Exception {
        User author = saveTestUser("author");
        User user1 = saveTestUser("user1");
        User user2 = saveTestUser("user2");
        Post post = postRepository.save(new Post(author, "title", "content", 2, PostStatus.OPEN));
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(post));
        chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        chatRoom.increaseMemberCount();
        chatRoomRepository.saveAndFlush(chatRoom);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            List<Callable<String>> tasks = List.of(
                () -> attemptJoin(post.getId(), user1.getId(), ready, start),
                () -> attemptJoin(post.getId(), user2.getId(), ready, start)
            );
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(executorService.submit(task));
            }

            ready.await();
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get());
            }

            ChatRoom refreshedRoom = chatRoomRepository.findById(chatRoom.getId())
                .orElseThrow(() -> new AssertionError("채팅방이 유지되어야 합니다."));

            assertThat(results).containsExactlyInAnyOrder("SUCCESS", "CHAT_ROOM_FULL");
            assertThat(refreshedRoom.getMemberCount()).isEqualTo(2);
            assertThat(chatRoomMemberRepository.countByRoomIdAndStatus(chatRoom.getId(), ChatRoomMemberStatus.ACTIVE))
                .isEqualTo(2);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("나가기 후 다시 참여해도 memberCount는 ACTIVE 수와 일치한다")
    void leaveAndRejoinKeepsMemberCountConsistent() {
        User author = saveTestUser("author");
        User member = saveTestUser("member");
        Post post = postRepository.save(new Post(author, "title", "content", 3, PostStatus.OPEN));
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(post));
        chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        chatRoom.increaseMemberCount();
        chatRoomRepository.saveAndFlush(chatRoom);

        chatMembershipService.joinPost(post.getId(), member.getId());
        chatMembershipService.leavePost(post.getId(), member.getId());
        chatMembershipService.joinPost(post.getId(), member.getId());

        ChatRoom refreshedRoom = chatRoomRepository.findById(chatRoom.getId())
            .orElseThrow(() -> new AssertionError("채팅방이 유지되어야 합니다."));
        ChatRoomMember refreshedMember = chatRoomMemberRepository.findByRoomIdAndUserId(chatRoom.getId(), member.getId())
            .orElseThrow(() -> new AssertionError("멤버 row가 유지되어야 합니다."));

        assertThat(refreshedRoom.getMemberCount()).isEqualTo(2);
        assertThat(chatRoomMemberRepository.countByRoomIdAndStatus(chatRoom.getId(), ChatRoomMemberStatus.ACTIVE))
            .isEqualTo(2);
        assertThat(refreshedMember.getStatus()).isEqualTo(ChatRoomMemberStatus.ACTIVE);
        assertThat(refreshedMember.getLeftAt()).isNull();
        assertThat(refreshedMember.getJoinedAt()).isNotNull();
    }

    private String attemptJoin(Long postId, Long userId, CountDownLatch ready, CountDownLatch start)
        throws Exception {
        ready.countDown();
        start.await();

        try {
            chatMembershipService.joinPost(postId, userId);
            return "SUCCESS";
        } catch (ChatRoomFullException exception) {
            return "CHAT_ROOM_FULL";
        }
    }

    private User saveTestUser(String prefix) {
        long sequence = TEST_USER_SEQUENCE.incrementAndGet();

        return userRepository.save(new User(
            prefix + "-" + sequence + "@test.com",
            "pw",
            prefix + "-" + sequence
        ));
    }
}
