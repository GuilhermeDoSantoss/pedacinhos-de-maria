package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.RegisterRequest;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.shared.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Casos de validação de entrada (email inválido, campos obrigatórios)
 * pertencem à camada de Bean Validation em RegisterRequest, não a este Use
 * Case — cobertos manualmente/no frontend, não duplicados aqui.
 */
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegisterUserUseCase useCase;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest("Maria Silva", "Maria@Exemplo.com", "senhaSegura123");
    }

    @Test
    void deveCadastrarUsuarioValidoComSucesso() {
        when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(false);
        when(passwordEncoder.encode("senhaSegura123")).thenReturn("$2a$10$hashSimulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = useCase.execute(validRequest);

        assertThat(response.email()).isEqualTo("maria@exemplo.com");
        assertThat(response.name()).isEqualTo("Maria Silva");
    }

    @Test
    void usuarioDeveNascerComStatusPending() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashSimulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = useCase.execute(validRequest);

        assertThat(response.status()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void usuarioDeveNascerComRoleKitchen() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashSimulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = useCase.execute(validRequest);

        assertThat(response.role()).isEqualTo(UserRole.KITCHEN);
    }

    @Test
    void senhaDeveSerArmazenadaComBCryptENuncaEmTextoPuro() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("senhaSegura123")).thenReturn("$2a$10$hashSimulado");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(validRequest);

        verify(passwordEncoder).encode("senhaSegura123");
        User persisted = userCaptor.getValue();
        assertThat(persisted.getPasswordHash())
                .isEqualTo("$2a$10$hashSimulado")
                .isNotEqualTo("senhaSegura123");
    }

    @Test
    void deveNormalizarEmailParaMinusculoESemEspacos() {
        RegisterRequest requestComEspacos = new RegisterRequest("Maria", "  Maria@Exemplo.com  ", "senha123");
        when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(requestComEspacos);

        verify(userRepository).existsByEmail("maria@exemplo.com");
    }

    @Test
    void deveLancarEmailAlreadyExistsExceptionQuandoEmailJaCadastrado() {
        when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(validRequest))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}