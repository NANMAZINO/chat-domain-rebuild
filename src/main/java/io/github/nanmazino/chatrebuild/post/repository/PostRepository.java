package io.github.nanmazino.chatrebuild.post.repository;

import io.github.nanmazino.chatrebuild.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

}
