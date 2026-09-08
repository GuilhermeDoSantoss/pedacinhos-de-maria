package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.shared.exception.UserAlreadyProcessedException;
import com.pedacinhodemaria.shared.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Substitui o antigo fluxo de aprovação via capability token/WhatsApp: a
 * aprovação agora é uma ação direta de um usuário OWNER autenticado (a
 * autorização ROLE_OWNER é garantida pelo SecurityConfig antes mesmo desta
 * classe ser chamada — este Use Case não reverifica role, só a transição de
 * estado do usuário alvo).
 *
 * Só aceita a transição PENDING → APPROVED. Um usuário já APPROVED ou
 * REJECTED não pode ser reaprovado por este caminho — evita processamento
 * duplicado (ex.: duplo clique no botão "Aprovar") sem precisar de
 * mecanismo de single-use como o token antigo tinha, porque o próprio
 * status do documento já é a guarda de idempotência.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApproveUserUseCase {

    private final UserRepository userRepository;

    public UserResponse execute(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserAlreadyProcessedException(userId, user.getStatus());
        }

        user.setStatus(UserStatus.APPROVED);
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        log.info("Usuário {} aprovado — pode fazer login", saved.getEmail());

        return toResponse(saved);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}