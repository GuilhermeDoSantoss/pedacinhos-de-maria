package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.dto.LoginRequest;
import com.pedacinhodemaria.modules.auth.dto.TokenResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A verificação de senha/status (BCrypt, PENDING/REJECTED bloqueados) é
 * responsabilidade do AuthenticationManager real (DaoAuthenticationProvider
 * + PedacinhoUserDetailsService) — aqui ele é mockado, então estes testes
 * cobrem a ORQUESTRAÇÃO deste Use Case: delega para o AuthenticationManager,
 * propaga a exceção dele sem mascarar, e só gera JWT se a autenticação
 * realmente passar. O comportamento real de bloqueio PENDING/REJECTED é
 * coberto pelo teste de PedacinhoUserDetailsService (enabled=false) mais o
 * comportamento documentado e padrão do DaoAuthenticationProvider do
 * próprio Spring Security, que não reimplementamos aqui.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticateUserUseCase useCase;

    private User approvedUser;

    @BeforeEach
    void setUp() {
        approvedUser = User.builder()
                .id("1")
                .name("Maria Silva")
                .email("maria@exemplo.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.KITCHEN)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void loginValidoDeUsuarioApprovedGeraToken() {
        LoginRequest request = new LoginRequest("Maria@Exemplo.com", "senhaCorreta");
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(approvedUser));
        when(jwtService.generateToken("maria@exemplo.com", UserRole.KITCHEN)).thenReturn("token-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        TokenResponse response = useCase.execute(request);

        assertThat(response.token()).isEqualTo("token-jwt");
        assertThat(response.type()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void normalizaEmailAntesDeAutenticar() {
        LoginRequest request = new LoginRequest("  Maria@Exemplo.com  ", "senhaCorreta");
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(approvedUser));
        when(jwtService.generateToken(any(), any())).thenReturn("token");

        useCase.execute(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("maria@exemplo.com");
    }

    @Test
    void senhaIncorretaPropagaBadCredentialsExceptionSemGerarToken() {
        LoginRequest request = new LoginRequest("maria@exemplo.com", "senhaErrada");
        doThrow(new BadCredentialsException("Credenciais inválidas"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> useCase.execute(request)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void usuarioPendingOuRejectedPropagaDisabledExceptionSemGerarToken() {
        LoginRequest request = new LoginRequest("pendente@exemplo.com", "senhaCorreta");
        doThrow(new DisabledException("Conta desabilitada"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> useCase.execute(request)).isInstanceOf(DisabledException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void emailInexistentePropagaBadCredentialsExceptionMesmoQueDaoAuthenticationProviderOculteUsernameNotFound() {
        // hideUserNotFoundExceptions=true é o padrão do DaoAuthenticationProvider:
        // e-mail inexistente também vira BadCredentialsException, nunca
        // UsernameNotFoundException, evitando enumeração de contas.
        LoginRequest request = new LoginRequest("naoexiste@exemplo.com", "qualquerSenha");
        doThrow(new BadCredentialsException("Credenciais inválidas"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> useCase.execute(request)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtService);
    }
}