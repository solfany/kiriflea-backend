package com.nplohs.market.config;

import com.nplohs.market.user.entity.User;
import com.nplohs.market.user.repository.UserRepository;
import com.nplohs.market.chat.entity.ChatRoom;
import com.nplohs.market.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects STOMP CONNECT frames without an authenticated principal and STOMP SUBSCRIBE
 * frames targeting a chat room/user topic the caller doesn't belong to. Without this,
 * anyone who can open a SockJS connection can subscribe to any /topic/chat/{roomId} or
 * /topic/user/{id}/** destination and read other users' chat/notification traffic live,
 * regardless of whether the CONNECT itself carried valid credentials.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;

    private static final Pattern CHAT_ROOM_TOPIC = Pattern.compile("^/topic/chat/(\\d+)(/read)?$");
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/user/(\\d+)/.+$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT && accessor.getUser() == null) {
            throw new MessagingException("인증되지 않은 연결입니다.");
        }

        if (command == StompCommand.SUBSCRIBE) {
            Principal principal = accessor.getUser();
            if (principal == null) {
                throw new MessagingException("인증되지 않은 구독 요청입니다.");
            }

            String destination = accessor.getDestination();
            if (destination != null) {
                Long userId = userRepository.findByEmail(principal.getName())
                        .map(User::getId)
                        .orElse(null);
                if (userId == null) {
                    throw new MessagingException("사용자를 찾을 수 없습니다.");
                }

                Matcher roomMatcher = CHAT_ROOM_TOPIC.matcher(destination);
                if (roomMatcher.matches()) {
                    Long roomId = Long.valueOf(roomMatcher.group(1));
                    ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
                    boolean allowed = room != null
                            && (room.getBuyer().getId().equals(userId) || room.getSeller().getId().equals(userId));
                    if (!allowed) {
                        throw new MessagingException("채팅방 구독 권한이 없습니다.");
                    }
                }

                Matcher userMatcher = USER_TOPIC.matcher(destination);
                if (userMatcher.matches()) {
                    Long targetId = Long.valueOf(userMatcher.group(1));
                    if (!userId.equals(targetId)) {
                        throw new MessagingException("구독 권한이 없습니다.");
                    }
                }
            }
        }

        return message;
    }
}
