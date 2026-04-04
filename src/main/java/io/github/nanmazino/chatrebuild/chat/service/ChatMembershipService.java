package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.exception.ChatMemberAlreadyActiveException;
import io.github.nanmazino.chatrebuild.chat.exception.ChatMemberNotFoundException;
import io.github.nanmazino.chatrebuild.chat.exception.ChatRoomFullException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.dto.response.JoinPostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.LeavePostResponse;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.exception.PostAlreadyClosedException;
import io.github.nanmazino.chatrebuild.post.exception.PostNotFoundException;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ChatMembershipService {

    private static final int MAX_LOCK_RETRIES = 3;

    private final PostRepository postRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate serializableTransactionTemplate;

    public ChatMembershipService(
        PostRepository postRepository,
        ChatRoomRepository chatRoomRepository,
        ChatRoomMemberRepository chatRoomMemberRepository,
        UserRepository userRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.postRepository = postRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.userRepository = userRepository;
        this.serializableTransactionTemplate = new TransactionTemplate(transactionManager);
        this.serializableTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public JoinPostResponse joinPost(Long postId, Long userId) {
        return executeWithLockRetry(() -> doJoinPost(postId, userId));
    }

    public LeavePostResponse leavePost(Long postId, Long userId) {
        return executeWithLockRetry(() -> doLeavePost(postId, userId));
    }

    public ChatRoomMember getActiveMember(Long roomId, Long userId) {
        return chatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId)
            .filter(member -> member.getStatus() == ChatRoomMemberStatus.ACTIVE)
            .orElseThrow(ChatMemberNotFoundException::new);
    }

    private JoinPostResponse doJoinPost(Long postId, Long userId) {
        return serializableTransactionTemplate.execute(status -> {
            Post post = getPost(postId);
            validateJoinablePost(post);

            ChatRoom chatRoom = getLockedChatRoom(postId);
            ChatRoomMember member = chatRoomMemberRepository.findByRoomIdAndUserId(chatRoom.getId(), userId)
                .orElse(null);

            if (member != null && member.getStatus() == ChatRoomMemberStatus.ACTIVE) {
                throw new ChatMemberAlreadyActiveException();
            }

            validateRoomCapacity(chatRoom, post.getMaxParticipants());

            LocalDateTime joinedAt = LocalDateTime.now();
            ChatRoomMember joinedMember = member == null
                ? createActiveMember(chatRoom, userId, joinedAt)
                : reactivateMember(member, joinedAt);

            chatRoom.increaseMemberCount();

            return new JoinPostResponse(
                post.getId(),
                chatRoom.getId(),
                joinedMember.getStatus(),
                chatRoom.getMemberCount(),
                joinedMember.getJoinedAt()
            );
        });
    }

    private LeavePostResponse doLeavePost(Long postId, Long userId) {
        return serializableTransactionTemplate.execute(status -> {
            Post post = getPost(postId);
            ChatRoom chatRoom = getLockedChatRoom(postId);
            ChatRoomMember member = getActiveMember(chatRoom.getId(), userId);

            LocalDateTime leftAt = LocalDateTime.now();
            member.leave(leftAt);
            chatRoom.decreaseMemberCount();

            return new LeavePostResponse(
                post.getId(),
                chatRoom.getId(),
                member.getStatus(),
                chatRoom.getMemberCount(),
                member.getLeftAt()
            );
        });
    }

    private <T> T executeWithLockRetry(MembershipAction<T> action) {
        CannotAcquireLockException lastException = null;

        for (int attempt = 1; attempt <= MAX_LOCK_RETRIES; attempt++) {
            try {
                return action.run();
            } catch (CannotAcquireLockException exception) {
                lastException = exception;
            }
        }

        throw lastException;
    }

    private Post getPost(Long postId) {
        return postRepository.findById(postId)
            .orElseThrow(PostNotFoundException::new);
    }

    private ChatRoom getLockedChatRoom(Long postId) {
        return chatRoomRepository.findByPostIdForUpdate(postId)
            .orElseThrow(PostNotFoundException::new);
    }

    private void validateJoinablePost(Post post) {
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException();
        }

        if (post.getStatus() == PostStatus.CLOSED) {
            throw new PostAlreadyClosedException();
        }
    }

    private void validateRoomCapacity(ChatRoom chatRoom, int maxParticipants) {
        if (chatRoom.getMemberCount() >= maxParticipants) {
            throw new ChatRoomFullException();
        }
    }

    private ChatRoomMember createActiveMember(ChatRoom chatRoom, Long userId, LocalDateTime joinedAt) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("인증 사용자를 찾을 수 없습니다."));

        return chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            user,
            ChatRoomMemberStatus.ACTIVE,
            joinedAt
        ));
    }

    private ChatRoomMember reactivateMember(ChatRoomMember member, LocalDateTime joinedAt) {
        member.reactivate(joinedAt);
        return member;
    }

    @FunctionalInterface
    private interface MembershipAction<T> {

        T run();
    }
}
