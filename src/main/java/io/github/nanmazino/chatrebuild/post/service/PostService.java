package io.github.nanmazino.chatrebuild.post.service;

import io.github.nanmazino.chatrebuild.post.dto.request.CreatePostRequest;
import io.github.nanmazino.chatrebuild.post.dto.response.CreatePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostAuthorResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostDetailResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostListResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostSummaryResponse;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.exception.PostNotFoundException;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final PostRepository postRepository;
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

        return new CreatePostResponse(
            savedPost.getId(),
            savedPost.getTitle(),
            savedPost.getContent(),
            savedPost.getMaxParticipants(),
            savedPost.getStatus(),
            toAuthorResponse(savedPost.getAuthor()),
            savedPost.getCreatedAt()
        );
    }

    public PostListResponse getPosts(Integer page, Integer size, PostStatus status, String keyword) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        List<PostStatus> statuses = status == null
            ? List.of(PostStatus.OPEN, PostStatus.CLOSED)
            : List.of(status);

        PageRequest pageable = PageRequest.of(
            resolvedPage,
            resolvedSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

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
        Post post = postRepository.findWithAuthorById(postId)
            .orElseThrow(PostNotFoundException::new);

        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException();
        }

        return new PostDetailResponse(
            post.getId(),
            post.getTitle(),
            post.getContent(),
            post.getMaxParticipants(),
            post.getStatus(),
            toAuthorResponse(post.getAuthor()),
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }

    private PostAuthorResponse toAuthorResponse(User author) {
        return new PostAuthorResponse(author.getId(), author.getNickname());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
