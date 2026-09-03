package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.ApprovalTokenGenerator;
import com.pedacinhodemaria.modules.order.service.WhatsAppMessageSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ponto de segurança central testado aqui: a mensagem vai para o telefone
 * DA DONA (config), nunca para o telefone do usuário que se cadastrou — o
 * User não tem nem campo de telefone neste módulo, então esse risco nem
 * existe estruturalmente, mas o teste deixa explícito o contrato.
 */
@ExtendWith(MockitoExtension.class)
class SendApprovalRequestWhatsAppMessageUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WhatsAppMessageSender whatsAppMessageSender;

    private final ApprovalTokenGenerator approvalTokenGenerator = new ApprovalTokenGenerator();

    private SendApprovalRequestWhatsAppMessageUseCase useCase;

    private User pendingUser;

    @BeforeEach
    void setUp() throws Exception {
        useCase = new SendApprovalRequestWhatsAppMessageUseCase(userRepository, approvalTokenGenerator, whatsAppMessageSender);
        setField("ownerPhoneNumber", "5511999998888");
        setField("tokenExpirationHours", 48L);
        setField("baseUrl", "https://pedacinho.onrender.com");

        pendingUser = User.builder()
                .id("1")
                .name("Maria Silva")
                .email("maria@exemplo.com")
                .role(UserRole.KITCHEN)
                .status(UserStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    private void setField(String name, Object value) throws Exception {
        var field = SendApprovalRequestWhatsAppMessageUseCase.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(useCase, value);
    }

    @Test
    void enviaMensagemParaOTelefoneDaDonaNaoDoUsuarioCadastrado() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(pendingUser);

        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppMessageSender).sendMessage(phoneCaptor.capture(), any());
        assertThat(phoneCaptor.getValue()).isEqualTo("5511999998888");
    }

    @Test
    void persisteHashDoTokenENuncaOTokenBrutoNoUser() {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(pendingUser);

        User saved = userCaptor.getValue();
        assertThat(saved.getApprovalTokenHash()).isNotNull().hasSize(64); // SHA-256 em hex
        assertThat(saved.getApprovalTokenExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void mensagemContemLinksDeAceitarERejeitarComABaseUrlConfigurada() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        useCase.execute(pendingUser);

        verify(whatsAppMessageSender).sendMessage(any(), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("https://pedacinho.onrender.com/api/v1/auth/approvals/")
                .contains("/accept")
                .contains("/reject");
    }

    @Test
    void naoEnviaMensagemQuandoOwnerPhoneNumberNaoConfigurado() throws Exception {
        setField("ownerPhoneNumber", "");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(pendingUser);

        verifyNoInteractions(whatsAppMessageSender);
    }

    @Test
    void aindaPersisteOTokenMesmoSemOwnerPhoneNumberConfigurado() throws Exception {
        setField("ownerPhoneNumber", "");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(pendingUser);

        assertThat(userCaptor.getValue().getApprovalTokenHash()).isNotNull();
    }
}