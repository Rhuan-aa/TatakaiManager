package com.tatakai.manager.websocket;

import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida o JWT no handshake STOMP (CONNECT): token válido e usuário ainda existente — NFR-03.
 * No SUBSCRIBE, só membros da campanha podem assinar /topic/campaign/{id}/{canal}.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CAMPAIGN_TOPIC =
            Pattern.compile("^/topic/campaign/([0-9a-fA-F-]{36})/[a-z-]+$");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CampaignMemberRepository campaignMemberRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, UserRepository userRepository,
                                       CampaignMemberRepository campaignMemberRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.campaignMemberRepository = campaignMemberRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = bearerToken(accessor);
            if (token == null || !jwtService.isValid(token)) {
                throw new MessagingException("Conexão WebSocket não autenticada");
            }
            String userId = jwtService.extractUserId(token);
            if (!userRepository.existsById(UUID.fromString(userId))) {
                throw new MessagingException("Conexão WebSocket não autenticada");
            }
            var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            accessor.setUser(auth);
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            checkSubscription(accessor);
        }
        return message;
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        var user = accessor.getUser();
        Matcher m = destination == null ? null : CAMPAIGN_TOPIC.matcher(destination);
        if (user == null || m == null || !m.matches()) {
            throw new MessagingException("Assinatura WebSocket não permitida");
        }
        try {
            UUID campaignId = UUID.fromString(m.group(1));
            if (campaignMemberRepository.existsByCampaignIdAndUserId(campaignId, UUID.fromString(user.getName()))) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // UUID malformado: cai na rejeição abaixo
        }
        throw new MessagingException("Você não é membro desta campanha");
    }

    private String bearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
