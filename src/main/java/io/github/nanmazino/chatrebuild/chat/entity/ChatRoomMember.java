package io.github.nanmazino.chatrebuild.chat.entity;

import io.github.nanmazino.chatrebuild.global.entity.BaseTimeEntity;
import io.github.nanmazino.chatrebuild.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "chat_room_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_chat_room_members_room_id_user_id",
            columnNames = {"room_id", "user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_chat_room_members_user_id_status_room_id",
            columnList = "user_id, status, room_id"),
        @Index(name = "idx_chat_room_members_room_id_status", columnList = "room_id, status")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomMemberStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column
    private LocalDateTime leftAt;

    @Column
    private Long lastReadMessageId;

    @Column
    private LocalDateTime lastReadAt;

    public ChatRoomMember(ChatRoom room, User user, ChatRoomMemberStatus status,
        LocalDateTime joinedAt) {
        this.room = room;
        this.user = user;
        this.status = status;
        this.joinedAt = joinedAt;
    }
}
