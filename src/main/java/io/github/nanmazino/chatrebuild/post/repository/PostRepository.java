package io.github.nanmazino.chatrebuild.post.repository;

import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<Post> findWithAuthorById(Long postId);

    @EntityGraph(attributePaths = "author")
    @Query("""
        select p
        from Post p
        where p.status in :statuses
          and (
            :keyword is null
            or lower(p.title) like lower(concat('%', :keyword, '%'))
            or lower(p.content) like lower(concat('%', :keyword, '%'))
          )
        """)
    Page<Post> findAllByStatusesAndKeyword(Collection<PostStatus> statuses, String keyword, Pageable pageable);
}
