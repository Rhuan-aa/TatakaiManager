package com.tatakai.manager.booking;

import com.tatakai.manager.entity.User;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.UserRepository;
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
import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("StompAuthChannelInterceptor — Sprint 6 (NFR-03)")
class StompAuthChannelInterceptorTest {

    private JwtService jwtService;
    private StompAuthChannelInterceptor interceptor;
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CampaignMemberRepository memberRepository = mock(CampaignMemberRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-for-unit-tests-only-32c", 3600000);
        interceptor = new StompAuthChannelInterceptor(jwtService, userRepository, memberRepository);
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
        when(userRepository.existsById(user.getId())).thenReturn(true);

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

    @Test
    @DisplayName("CONNECT com token válido de usuário inexistente é rejeitado")
    void connect_withDeletedUser_isRejected() {
        User user = User.builder().id(UUID.randomUUID()).name("Ana").email("ana@rpg.com").build();
        String token = jwtService.generateToken(user);
        when(userRepository.existsById(user.getId())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer " + token), channel))
                .isInstanceOf(MessagingException.class);
    }

    private Message<byte[]> subscribeMessage(UUID userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Principal principal = () -> userId.toString();
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("SUBSCRIBE em canal da campanha é aceito para membro")
    void subscribe_member_isAllowed() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(memberRepository.existsByCampaignIdAndUserId(campaignId, userId)).thenReturn(true);
        Message<byte[]> msg = subscribeMessage(userId, "/topic/campaign/" + campaignId + "/sheets");

        assertThat(interceptor.preSend(msg, channel)).isSameAs(msg);
    }

    @Test
    @DisplayName("SUBSCRIBE é negado para quem não é membro da campanha")
    void subscribe_nonMember_isDenied() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(memberRepository.existsByCampaignIdAndUserId(campaignId, userId)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage(userId, "/topic/campaign/" + campaignId + "/slots"), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE fora de /topic/campaign/{uuid}/{canal} é negado")
    void subscribe_outsideCampaignTopic_isDenied() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage(userId, "/topic/other"), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage(userId, "/topic/campaign/not-a-uuid/slots"), channel))
                .isInstanceOf(MessagingException.class);
    }
}
