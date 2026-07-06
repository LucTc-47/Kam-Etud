package com.kametude.support_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Token JWT manquant ou mal formé");
            }

            String token = authHeader.substring(7); // enlève "Bearer "

            if (!jwtUtils.isTokenValid(token)) {
                throw new IllegalArgumentException("Token JWT invalide ou expiré");
            }

            String username = jwtUtils.extractUsername(token);
            UUID userId = jwtUtils.extractUserId(token);
            String role = jwtUtils.extractRole(token);

            StompPrincipal principal = new StompPrincipal(userId, username, role);
            accessor.setUser(principal);
        }

        return message;
    }
}