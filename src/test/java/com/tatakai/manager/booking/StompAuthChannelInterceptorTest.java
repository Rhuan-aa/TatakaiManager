package com.tatakai.manager.booking;

import com.tatakai.manager.entity.User;
import com.tatakai.manager.security.JwtService;
import com.tatakai.manager.websocket.StompAuthChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("StompAuthChannelInterceptor — Sprint 6 (NFR-03)")
class StompAuthChannelInterceptorTest {

    private JwtService jwtService;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-for-unit-tests-only-32c", 3600000);
        interceptor = new StompAuthChannelInterceptor(jwtService);
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        // Simula o canal de entrada do Spring, que entrega acessores mutáveis aos interceptors
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT com token válido é aceito e define o principal")
    void connect_withValidToken_setsPrincipal() {
        User user = User.builder().id(UUID.randomUUID()).name("Ana").email("ana@rpg.com").build();
        String token = jwtService.generateToken(user);

        Message<?> result = interceptor.preSend(connectMessage("Bearer " + token), channel);

        StompHeaderAccessor out =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(out).isNotNull();
        assertThat(out.getUser()).isNotNull();
        assertThat(out.getUser().getName()).isEqualTo(user.getId().toString());
    }

    @Test
    @DisplayName("CONNECT sem token é rejeitado")
    void connect_withoutToken_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    @DisplayName("CONNECT com token inválido é rejeitado")
    void connect_withInvalidToken_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer not-a-jwt"), channel))
                .isInstanceOf(MessagingException.class);
    }
}
