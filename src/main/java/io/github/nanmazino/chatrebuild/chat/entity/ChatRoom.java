package io.github.nanmazino.chatrebuild.chat.entity;

import io.github.nanmazino.chatrebuild.global.entity.BaseTimeEntity;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "chat_rooms",
    indexes = {
        @Index(name = "idx_chat_rooms_last_message_at_id", columnList = "last_message_at, id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Column(nullable = false)
    private int memberCount;

    @Column
    private Long lastMessageId;

    @Column(length = 255)
    private String lastMessagePreview;

    @Column
    private LocalDateTime lastMessageAt;

    public ChatRoom(Post post) {
        this.post = post;
        this.memberCount = 0;
    }

    public void increaseMemberCount() {
        this.memberCount++;
    }
}
