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
 * NOTA: não tenho acesso ao RegisterRequest.java / UserResponse.java reais
 * (não foram anexados), então estou assumindo que são records com os
 * accessors usados em RegisterUserUseCase (request.name(), request.email(),
 * request.password()). Se algum desses tipos não for record, ajuste a
 * construção abaixo — a lógica dos testes não muda.
 *
 * Casos de validação de entrada (email inválido, campos obrigatórios — itens
 * 8/9/10 da lista original) pertencem à camada de Bean Validation em
 * RegisterRequest, não a este Use Case, e exigem o arquivo real para não
 * inventar anotações que talvez não existam. Ficam como pendência.
 */
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // NOVO (Fase 2A): RegisterUserUseCase agora chama este use case após
    // salvar — sem o mock, @InjectMocks passaria null e o execute() real
    // lançaria NullPointerException em todo teste de sucesso.
    @Mock
    private SendApprovalRequestWhatsAppMessageUseCase sendApprovalRequestWhatsAppMessageUseCase;

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
    void deveDispararSolicitacaoDeAprovacaoAposCadastroValido() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashSimulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(validRequest);

        verify(sendApprovalRequestWhatsAppMessageUseCase).execute(any(User.class));
    }

    @Test
    void naoDeveDispararSolicitacaoDeAprovacaoQuandoEmailJaExiste() {
        when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(validRequest)).isInstanceOf(EmailAlreadyExistsException.class);

        verify(sendApprovalRequestWhatsAppMessageUseCase, never()).execute(any(User.class));
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