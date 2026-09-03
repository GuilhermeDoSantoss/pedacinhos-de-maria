package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.ApprovalTokenGenerator;
import com.pedacinhodemaria.shared.exception.InvalidApprovalTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Valida o capability token recebido no link do WhatsApp e aplica a
 * transição PENDING → APPROVED ou PENDING → REJECTED.
 *
 * Ordem de validação importa por segurança: primeiro localizamos pelo
 * hash (token precisa existir), depois checamos status==PENDING (protege
 * contra reaproveitar um token velho num usuário já processado), depois
 * expiração — qualquer falha aqui lança a MESMA InvalidApprovalTokenException.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessUserApprovalUseCase {

    private final UserRepository userRepository;
    private final ApprovalTokenGenerator approvalTokenGenerator;

    public enum Decision { ACCEPT, REJECT }

    public User execute(String rawToken, Decision decision) {
        String tokenHash = approvalTokenGenerator.hash(rawToken);

        User user = userRepository.findByApprovalTokenHash(tokenHash)
                .orElseThrow(InvalidApprovalTokenException::new);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new InvalidApprovalTokenException();
        }

        if (user.getApprovalTokenExpiresAt() == null || user.getApprovalTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidApprovalTokenException();
        }

        user.setStatus(decision == Decision.ACCEPT ? UserStatus.APPROVED : UserStatus.REJECTED);
        // Single-use: token é limpo assim que consumido, independente do
        // resultado — nem aceitar nem rejeitar podem ser repetidos com o
        // mesmo link (uma segunda tentativa cai no findByApprovalTokenHash
        // vazio acima, mesma exceção genérica).
        user.setApprovalTokenHash(null);
        user.setApprovalTokenExpiresAt(null);
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        log.info("Cadastro de {} processado: {}", saved.getEmail(), saved.getStatus());
        return saved;
    }
}