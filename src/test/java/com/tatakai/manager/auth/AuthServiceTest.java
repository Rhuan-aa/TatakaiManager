package com.tatakai.manager.auth;

import com.tatakai.manager.dto.request.LoginRequest;
import com.tatakai.manager.dto.request.RegisterRequest;
import com.tatakai.manager.dto.response.AuthResponse;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.EmailAlreadyUsedException;
import com.tatakai.manager.exception.InvalidCredentialsException;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.security.JwtService;
import com.tatakai.manager.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Sprint 1 (US-01, US-02)")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    // Encoder real (rápido o suficiente) para validar hash/verify de verdade
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("US-01: cadastro com e-mail novo persiste usuário e retorna token")
    void register_withNewEmail_returnsToken() {
        var req = new RegisterRequest("Ana", "ana@rpg.com", "senha12345");
        when(userRepository.existsByEmail("ana@rpg.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse res = authService.register(req);

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.name()).isEqualTo("Ana");
        assertThat(res.email()).isEqualTo("ana@rpg.com");

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // senha nunca deve ser persistida em texto puro
        assertThat(captor.getValue().getPassword()).isNotEqualTo("senha12345");
        assertThat(passwordEncoder.matches("senha12345", captor.getValue().getPassword())).isTrue();
    }

    @Test
    @DisplayName("US-01: cadastro com e-mail já existente é rejeitado")
    void register_withDuplicateEmail_throws() {
        var req = new RegisterRequest("Ana", "ana@rpg.com", "senha12345");
        when(userRepository.existsByEmail("ana@rpg.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-02: login com credenciais válidas retorna token")
    void login_withValidCredentials_returnsToken() {
        var user = User.builder()
                .id(UUID.randomUUID())
                .name("Bruno")
                .email("bruno@rpg.com")
                .password(passwordEncoder.encode("senhaCerta1"))
                .build();
        when(userRepository.findByEmail("bruno@rpg.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse res = authService.login(new LoginRequest("bruno@rpg.com", "senhaCerta1"));

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.email()).isEqualTo("bruno@rpg.com");
    }

    @Test
    @DisplayName("US-02: login com senha errada é rejeitado")
    void login_withWrongPassword_throws() {
        var user = User.builder()
                .id(UUID.randomUUID())
                .name("Bruno")
                .email("bruno@rpg.com")
                .password(passwordEncoder.encode("senhaCerta1"))
                .build();
        when(userRepository.findByEmail("bruno@rpg.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("bruno@rpg.com", "senhaErrada")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("US-02: login com e-mail inexistente é rejeitado")
    void login_withUnknownEmail_throws() {
        when(userRepository.findByEmail("ghost@rpg.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@rpg.com", "qualquer1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
