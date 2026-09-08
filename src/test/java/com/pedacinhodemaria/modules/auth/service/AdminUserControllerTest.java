package com.pedacinhodemaria.modules.auth.controller;

import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.service.ApproveUserUseCase;
import com.pedacinhodemaria.modules.auth.service.ListUsersUseCase;
import com.pedacinhodemaria.modules.auth.service.RejectUserUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário do controller (use cases mockados) — cobre que os métodos
 * delegam corretamente e devolvem os status HTTP esperados.
 *
 * IMPORTANTE: isto NÃO é o teste de segurança HTTP real pedido (OWNER→200,
 * KITCHEN→403, sem JWT→401). Esse teste exige @SpringBootTest/@WebMvcTest
 * com o SecurityFilterChain real carregado — o padrão de testes deste
 * projeto até agora é 100% Mockito puro, sem contexto Spring, e este
 * sandbox não tem acesso ao Maven Central para baixar as dependências
 * necessárias mesmo que eu escrevesse esse teste (mesma limitação de rede
 * já demonstrada nas fases anteriores). Recomendo fortemente rodar esse
 * teste de integração no seu ambiente local/CI antes de considerar a
 * proteção do /admin/** validada de ponta a ponta — deixo o roteiro exato
 * no relatório final.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private ListUsersUseCase listUsersUseCase;

    @Mock
    private ApproveUserUseCase approveUserUseCase;

    @Mock
    private RejectUserUseCase rejectUserUseCase;

    @InjectMocks
    private AdminUserController controller;

    private UserResponse sampleUser(UserStatus status) {
        return new UserResponse("1", "João Silva", "joao@exemplo.com", UserRole.KITCHEN, status, Instant.now());
    }

    @Test
    void listDelegaParaListUsersUseCaseComOStatusRecebido() {
        when(listUsersUseCase.execute(UserStatus.PENDING)).thenReturn(List.of(sampleUser(UserStatus.PENDING)));

        ResponseEntity<List<UserResponse>> response = controller.list(UserStatus.PENDING);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(listUsersUseCase).execute(eq(UserStatus.PENDING));
    }

    @Test
    void listSemFiltroDelegaComStatusNulo() {
        when(listUsersUseCase.execute(null)).thenReturn(List.of(sampleUser(UserStatus.PENDING), sampleUser(UserStatus.APPROVED)));

        ResponseEntity<List<UserResponse>> response = controller.list(null);

        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void approveDelegaParaApproveUserUseCaseComOIdDaUrl() {
        when(approveUserUseCase.execute("1")).thenReturn(sampleUser(UserStatus.APPROVED));

        ResponseEntity<UserResponse> response = controller.approve("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(UserStatus.APPROVED);
        verify(approveUserUseCase).execute("1");
    }

    @Test
    void rejectDelegaParaRejectUserUseCaseComOIdDaUrl() {
        when(rejectUserUseCase.execute("1")).thenReturn(sampleUser(UserStatus.REJECTED));

        ResponseEntity<UserResponse> response = controller.reject("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(UserStatus.REJECTED);
        verify(rejectUserUseCase).execute("1");
    }
}