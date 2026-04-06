package io.github.nanmazino.chatrebuild.chat.query;

import static io.github.nanmazino.chatrebuild.chat.entity.QChatMessage.chatMessage;
import static io.github.nanmazino.chatrebuild.chat.entity.QChatRoom.chatRoom;
import static io.github.nanmazino.chatrebuild.chat.entity.QChatRoomMember.chatRoomMember;
import static io.github.nanmazino.chatrebuild.post.entity.QPost.post;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomSummaryResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRoomQueryRepositoryImpl implements ChatRoomQueryRepository {

    private final JPAQueryFactory queryFactory;

    public ChatRoomQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<ChatRoomSummaryResponse> findMyChatRooms(
        Long userId,
        LocalDateTime cursorLastMessageAt,
        Long cursorRoomId,
        int size,
        String keyword
    ) {
        return queryFactory
            .select(Projections.constructor(
                ChatRoomSummaryResponse.class,
                chatRoom.id,
                post.id,
                post.title,
                chatRoom.memberCount,
                chatRoom.lastMessageId,
                chatRoom.lastMessagePreview,
                chatRoom.lastMessageAt,
                chatRoomMember.lastReadMessageId,
                unreadCountExpression()
            ))
            .from(chatRoomMember)
            .join(chatRoomMember.room, chatRoom)
            .join(chatRoom.post, post)
            .where(
                chatRoomMember.user.id.eq(userId),
                chatRoomMember.status.eq(ChatRoomMemberStatus.ACTIVE),
                keywordCondition(keyword),
                cursorCondition(cursorLastMessageAt, cursorRoomId)
            )
            .orderBy(
                nullBucketOrder(),
                chatRoom.lastMessageAt.desc(),
                chatRoom.id.desc()
            )
            .limit(size + 1L)
            .fetch();
    }

    private BooleanExpression keywordCondition(String keyword) {
        if (keyword == null) {
            return null;
        }

        return post.title.containsIgnoreCase(keyword);
    }

    private BooleanExpression cursorCondition(LocalDateTime cursorLastMessageAt, Long cursorRoomId) {
        if (cursorLastMessageAt == null && cursorRoomId == null) {
            return null;
        }

        if (cursorLastMessageAt == null) {
            return chatRoom.lastMessageAt.isNull()
                .and(chatRoom.id.lt(cursorRoomId));
        }

        return chatRoom.lastMessageAt.isNull()
            .or(chatRoom.lastMessageAt.lt(cursorLastMessageAt))
            .or(chatRoom.lastMessageAt.eq(cursorLastMessageAt)
                .and(chatRoom.id.lt(cursorRoomId)));
    }

    private Expression<Long> unreadCountExpression() {
        return JPAExpressions.select(chatMessage.count())
            .from(chatMessage)
            .where(
                chatMessage.room.id.eq(chatRoom.id),
                chatRoomMember.lastReadMessageId.isNull()
                    .or(chatMessage.id.gt(chatRoomMember.lastReadMessageId))
            );
    }

    private OrderSpecifier<Integer> nullBucketOrder() {
        return new CaseBuilder()
            .when(chatRoom.lastMessageAt.isNull()).then(1)
            .otherwise(0)
            .asc();
    }
}
