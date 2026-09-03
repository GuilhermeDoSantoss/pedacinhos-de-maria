package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.ApprovalTokenGenerator;
import com.pedacinhodemaria.modules.order.service.WhatsAppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Gera o capability token de aprovação, persiste hash + expiração no
 * próprio User (sem coleção nova — só existe um fluxo de aprovação ativo
 * por usuário por vez, já que e-mail duplicado é bloqueado no cadastro) e
 * envia o link para o WhatsApp da dona.
 *
 * Reaproveita EXATAMENTE o mesmo WhatsAppMessageSender que
 * SendOrderReadyWhatsAppMessageUseCase já usa — nenhuma infraestrutura de
 * WhatsApp nova foi criada, nenhuma segunda implementação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SendApprovalRequestWhatsAppMessageUseCase {

    private final UserRepository userRepository;
    private final ApprovalTokenGenerator approvalTokenGenerator;
    private final WhatsAppMessageSender whatsAppMessageSender;

    @Value("${app.whatsapp.owner-phone-number}")
    private String ownerPhoneNumber;

    @Value("${app.approval.token-expiration-hours}")
    private long tokenExpirationHours;

    @Value("${app.approval.base-url}")
    private String baseUrl;

    public void execute(User user) {
        ApprovalTokenGenerator.GeneratedToken generated = approvalTokenGenerator.generate();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(tokenExpirationHours));

        user.setApprovalTokenHash(generated.tokenHash());
        user.setApprovalTokenExpiresAt(expiresAt);
        userRepository.save(user);

        if (ownerPhoneNumber == null || ownerPhoneNumber.isBlank()) {
            // Mesmo comportamento gracioso do restante da integração de
            // WhatsApp (ver WhatsAppCloudApiMessageSender): falta de
            // configuração não derruba o cadastro, só significa que a
            // notificação não sai agora. O usuário continua PENDING
            // corretamente, aguardando aprovação por outro meio.
            log.warn("app.whatsapp.owner-phone-number não configurado — solicitação de aprovação de {} não foi enviada", user.getEmail());
            return;
        }

        String acceptUrl = baseUrl + "/api/v1/auth/approvals/" + generated.rawToken() + "/accept";
        String rejectUrl = baseUrl + "/api/v1/auth/approvals/" + generated.rawToken() + "/reject";

        String message = """
                Nova solicitação de acesso à Cozinha \uD83D\uDC69\u200D\uD83C\uDF73
                Nome: %s
                E-mail: %s

                Aceitar: %s
                Rejeitar: %s""".formatted(user.getName(), user.getEmail(), acceptUrl, rejectUrl);

        // O token bruto só existe aqui, dentro da mensagem — nunca logado,
        // nunca devolvido em nenhum response da API.
        whatsAppMessageSender.sendMessage(ownerPhoneNumber, message);
        log.info("Solicitação de aprovação enviada ao WhatsApp da dona para o cadastro de {}", user.getEmail());
    }
}