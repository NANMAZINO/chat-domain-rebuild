package io.github.nanmazino.chatrebuild.post.entity;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.global.entity.BaseTimeEntity;
import io.github.nanmazino.chatrebuild.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Table(
    name = "posts",
    indexes = {
        @Index(name = "idx_posts_author_id", columnList = "author_id"),
        @Index(name = "idx_posts_status_created_at", columnList = "status, created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @OneToOne(mappedBy = "post", fetch = FetchType.LAZY)
    private ChatRoom chatRoom;

    public Post(User author, String title, String content, int maxParticipants, PostStatus status) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.maxParticipants = maxParticipants;
        this.status = status;
    }

    public void update(String title, String content, int maxParticipants) {
        this.title = title;
        this.content = content;
        this.maxParticipants = maxParticipants;
    }

    public void close() {
        this.status = PostStatus.CLOSED;
    }

    public void delete() {
        this.status = PostStatus.DELETED;
    }
}
