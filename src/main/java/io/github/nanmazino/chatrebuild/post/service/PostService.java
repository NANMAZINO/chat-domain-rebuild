package io.github.nanmazino.chatrebuild.post.service;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.exception.ChatMemberAlreadyActiveException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.dto.request.CreatePostRequest;
import io.github.nanmazino.chatrebuild.post.dto.response.CreatePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.ClosePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.DeletePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.JoinPostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostAuthorResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostDetailResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostListResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostSummaryResponse;
import io.github.nanmazino.chatrebuild.post.dto.request.UpdatePostRequest;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.exception.PostAlreadyClosedException;
import io.github.nanmazino.chatrebuild.post.exception.PostNotFoundException;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final PostRepository postRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreatePostResponse createPost(Long authorId, CreatePostRequest request) {
        User author = userRepository.findById(authorId)
            .orElseThrow(() -> new IllegalStateException("인증 사용자를 찾을 수 없습니다."));

        Post post = new Post(
            author,
            request.title(),
            request.content(),
            request.maxParticipants(),
            PostStatus.OPEN
        );

        Post savedPost = postRepository.save(post);
        ChatRoom savedChatRoom = chatRoomRepository.save(new ChatRoom(savedPost));
        chatRoomMemberRepository.save(new ChatRoomMember(
            savedChatRoom,
            author,
            ChatRoomMemberStatus.ACTIVE,
            LocalDateTime.now()
        ));
        savedChatRoom.increaseMemberCount();

        return new CreatePostResponse(
            savedPost.getId(),
            savedChatRoom.getId(),
            savedPost.getTitle(),
            savedPost.getContent(),
            savedPost.getMaxParticipants(),
            savedPost.getStatus(),
            toAuthorResponse(savedPost.getAuthor()),
            savedPost.getCreatedAt()
        );
    }

    public PostListResponse getPosts(Integer page, Integer size, PostStatus status,
        String keyword) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        PageRequest pageable = PageRequest.of(
            resolvedPage,
            resolvedSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        if (status == PostStatus.DELETED) {
            return new PostListResponse(
                Collections.emptyList(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                false
            );
        }

        List<PostStatus> statuses = status == null
            ? List.of(PostStatus.OPEN, PostStatus.CLOSED)
            : List.of(status);

        Page<Post> result = postRepository.findAllByStatusesAndKeyword(
            statuses,
            hasText(keyword) ? keyword.trim() : null,
            pageable
        );

        List<PostSummaryResponse> items = result.getContent()
            .stream()
            .map(postItem -> new PostSummaryResponse(
                postItem.getId(),
                postItem.getTitle(),
                postItem.getMaxParticipants(),
                postItem.getStatus(),
                postItem.getAuthor().getNickname(),
                postItem.getCreatedAt()
            ))
            .toList();

        return new PostListResponse(
            items,
            result.getNumber(),
            result.getSize(),
            result.hasNext()
        );
    }

    public PostDetailResponse getPost(Long postId) {
        Post post = getActiveOrClosedPost(postId);

        return new PostDetailResponse(
            post.getId(),
            post.getChatRoom().getId(),
            post.getTitle(),
            post.getContent(),
            post.getMaxParticipants(),
            post.getStatus(),
            toAuthorResponse(post.getAuthor()),
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }

    @Transactional
    public JoinPostResponse joinPost(Long postId, Long userId) {
        Post post = getPostForMutation(postId);
        validateJoinablePost(post);

        ChatRoom chatRoom = post.getChatRoom();
        LocalDateTime joinedAt = LocalDateTime.now();
        ChatRoomMember joinedMember = chatRoomMemberRepository.findByRoomIdAndUserId(chatRoom.getId(), userId)
            .map(existingMember -> restoreOrRejectMember(existingMember, joinedAt))
            .orElseGet(() -> createActiveMember(chatRoom, userId, joinedAt));
        chatRoom.increaseMemberCount();

        return new JoinPostResponse(
            post.getId(),
            chatRoom.getId(),
            joinedMember.getStatus(),
            chatRoom.getMemberCount(),
            joinedMember.getJoinedAt()
        );
    }

    @Transactional
    public PostDetailResponse updatePost(Long postId, Long authorId, UpdatePostRequest request) {
        Post post = getActiveOrClosedPost(postId);
        validateAuthor(post, authorId);

        post.update(request.title(), request.content(), request.maxParticipants());
        Post updatedPost = postRepository.saveAndFlush(post);

        return new PostDetailResponse(
            updatedPost.getId(),
            updatedPost.getChatRoom().getId(),
            updatedPost.getTitle(),
            updatedPost.getContent(),
            updatedPost.getMaxParticipants(),
            updatedPost.getStatus(),
            toAuthorResponse(updatedPost.getAuthor()),
            updatedPost.getCreatedAt(),
            updatedPost.getUpdatedAt()
        );
    }

    @Transactional
    public ClosePostResponse closePost(Long postId, Long authorId) {
        Post post = getPostForMutation(postId);
        validateAuthor(post, authorId);

        if (post.getStatus() == PostStatus.CLOSED) {
            throw new PostAlreadyClosedException();
        }

        post.close();
        Post closedPost = postRepository.saveAndFlush(post);

        return new ClosePostResponse(
            closedPost.getId(),
            closedPost.getStatus(),
            closedPost.getUpdatedAt()
        );
    }

    @Transactional
    public DeletePostResponse deletePost(Long postId, Long authorId) {
        Post post = getPostForMutation(postId);
        validateAuthor(post, authorId);

        post.delete();
        Post deletedPost = postRepository.saveAndFlush(post);

        return new DeletePostResponse(
            deletedPost.getId(),
            deletedPost.getStatus(),
            deletedPost.getUpdatedAt()
        );
    }

    private PostAuthorResponse toAuthorResponse(User author) {
        return new PostAuthorResponse(author.getId(), author.getNickname());
    }

    private Post getActiveOrClosedPost(Long postId) {
        Post post = getPostForMutation(postId);

        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException();
        }

        return post;
    }

    private Post getPostForMutation(Long postId) {
        return postRepository.findWithAuthorAndChatRoomById(postId)
            .orElseThrow(PostNotFoundException::new);
    }

    private ChatRoomMember restoreOrRejectMember(ChatRoomMember existingMember, LocalDateTime joinedAt) {
        if (existingMember.getStatus() == ChatRoomMemberStatus.ACTIVE) {
            throw new ChatMemberAlreadyActiveException();
        }

        existingMember.reactivate(joinedAt);
        return existingMember;
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

    private void validateJoinablePost(Post post) {
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException();
        }

        if (post.getStatus() == PostStatus.CLOSED) {
            throw new PostAlreadyClosedException();
        }
    }

    private void validateAuthor(Post post, Long authorId) {
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException();
        }

        if (!post.getAuthor().getId().equals(authorId)) {
            throw new AccessDeniedException("게시글 작성자만 접근할 수 있습니다.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
