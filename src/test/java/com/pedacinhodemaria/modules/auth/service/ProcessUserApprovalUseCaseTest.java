package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.ApprovalTokenGenerator;
import com.pedacinhodemaria.shared.exception.InvalidApprovalTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessUserApprovalUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private final ApprovalTokenGenerator approvalTokenGenerator = new ApprovalTokenGenerator();

    private ProcessUserApprovalUseCase useCase;

    private static final String RAW_TOKEN = "token-bruto-de-teste";

    @BeforeEach
    void setUp() {
        useCase = new ProcessUserApprovalUseCase(userRepository, approvalTokenGenerator);
    }

    private User pendingUserWithToken(Instant expiresAt) {
        return User.builder()
                .id("1")
                .name("Maria")
                .email("maria@exemplo.com")
                .status(UserStatus.PENDING)
                .approvalTokenHash(approvalTokenGenerator.hash(RAW_TOKEN))
                .approvalTokenExpiresAt(expiresAt)
                .build();
    }

    @Test
    void aceitarTokenValidoTransicionaParaApproved() {
        User user = pendingUserWithToken(Instant.now().plus(1, ChronoUnit.DAYS));
        when(userRepository.findByApprovalTokenHash(approvalTokenGenerator.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.ACCEPT);

        assertThat(result.getStatus()).isEqualTo(UserStatus.APPROVED);
    }

    @Test
    void rejeitarTokenValidoTransicionaParaRejected() {
        User user = pendingUserWithToken(Instant.now().plus(1, ChronoUnit.DAYS));
        when(userRepository.findByApprovalTokenHash(any())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.REJECT);

        assertThat(result.getStatus()).isEqualTo(UserStatus.REJECTED);
    }

    @Test
    void tokenLimpoAposUso() {
        User user = pendingUserWithToken(Instant.now().plus(1, ChronoUnit.DAYS));
        when(userRepository.findByApprovalTokenHash(any())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.ACCEPT);

        assertThat(result.getApprovalTokenHash()).isNull();
        assertThat(result.getApprovalTokenExpiresAt()).isNull();
    }

    @Test
    void tokenExpiradoLancaInvalidApprovalTokenException() {
        User user = pendingUserWithToken(Instant.now().minus(1, ChronoUnit.HOURS));
        when(userRepository.findByApprovalTokenHash(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.ACCEPT))
                .isInstanceOf(InvalidApprovalTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void tokenInexistenteLancaInvalidApprovalTokenException() {
        when(userRepository.findByApprovalTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.ACCEPT))
                .isInstanceOf(InvalidApprovalTokenException.class);
    }

    @Test
    void usuarioJaApprovedNaoPodeSerReprocessado() {
        User user = pendingUserWithToken(Instant.now().plus(1, ChronoUnit.DAYS));
        user.setStatus(UserStatus.APPROVED); // já processado por um link anterior
        when(userRepository.findByApprovalTokenHash(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.ACCEPT))
                .isInstanceOf(InvalidApprovalTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void usuarioJaRejectedNaoPodeSerReprocessado() {
        User user = pendingUserWithToken(Instant.now().plus(1, ChronoUnit.DAYS));
        user.setStatus(UserStatus.REJECTED);
        when(userRepository.findByApprovalTokenHash(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.REJECT))
                .isInstanceOf(InvalidApprovalTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void tokenReutilizadoAposPrimeiroUsoFalha() {
        // Simula o segundo clique: primeira chamada consome o token (mock
        // devolve o user já com status alterado e token nulo), segunda
        // chamada com o mesmo hash não encontra mais ninguém pendente.
        when(userRepository.findByApprovalTokenHash(any()))
                .thenReturn(Optional.empty()); // token já foi limpo do banco

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN, ProcessUserApprovalUseCase.Decision.ACCEPT))
                .isInstanceOf(InvalidApprovalTokenException.class);
    }
}