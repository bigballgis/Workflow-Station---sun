package com.portal.config;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenService jwtTokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtTokenService.validateToken(token)) {
                        UserPrincipal userPrincipal = jwtTokenService.extractUserPrincipal(token);
                        String userId = userPrincipal.getUserId();

                        if (userId != null) {
                            final String finalUserId = userId;
                            accessor.setUser(new Principal() {
                                @Override
                                public String getName() {
                                    return finalUserId;
                                }
                            });
                            log.debug("WebSocket连接认证成功: userId={}", userId);
                        }
                    } else {
                        log.warn("WebSocket JWT认证失败: token validation failed");
                    }
                } catch (Exception e) {
                    log.warn("WebSocket JWT认证失败: {}", e.getMessage());
                }
            }
        }

        return message;
    }
}
