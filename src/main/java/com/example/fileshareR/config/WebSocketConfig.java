package com.example.fileshareR.config;

import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * STOMP over WebSocket — realtime notification transport.
 *
 * Endpoint:    /ws            (SockJS fallback)
 * App prefix:  /app/*          (client→server, currently unused — server pushes only)
 * Broker:      /topic/*        (broadcast — e.g. /topic/admin)
 *              /queue/*        (user-specific via convertAndSendToUser)
 * User dest:   /user/queue/notifications  (per-user inbox)
 *
 * Auth: JWT lifted from CONNECT frame `Authorization: Bearer ...` header;
 *       validated via SecurityUtil and bound as STOMP user-principal so
 *       SimpMessagingTemplate.convertAndSendToUser(email, ...) routes correctly.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SecurityUtil securityUtil;
    private final UserService userService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        log.warn("STOMP CONNECT rejected — missing Bearer token");
                        return null; // reject
                    }
                    String token = authHeader.substring("Bearer ".length()).trim();
                    try {
                        Jwt jwt = securityUtil.checkValidAccessToken(token);
                        String email = jwt.getSubject();

                        // Resolve user to also surface role authorities (useful for /topic/admin)
                        Optional<User> userOpt = userService.getUserByEmail(email);
                        List<SimpleGrantedAuthority> authorities = userOpt
                                .map(u -> List.of(new SimpleGrantedAuthority(
                                        "ROLE_" + u.getRole().name())))
                                .orElseGet(List::of);

                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(email, null, authorities);

                        accessor.setUser((Principal) auth);
                        log.debug("STOMP CONNECT accepted: {}", email);
                    } catch (Exception e) {
                        log.warn("STOMP CONNECT rejected — invalid JWT: {}", e.getMessage());
                        return null; // reject
                    }
                }
                return message;
            }
        });
    }
}
