package com.portal.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret:your-256-bit-secret-key-for-development-only}")
    private String jwtSecret;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                    Claims claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    String userId = claims.getSubject();
                    if (userId == null) {
                        userId = claims.get("userId", String.class);
                    }

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
                } catch (Exception e) {
                    log.warn("WebSocket JWT认证失败: {}", e.getMessage());
                }
            }
        }

        return message;
    }
}
