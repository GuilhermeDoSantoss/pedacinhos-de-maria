package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.RegisterRequest;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.shared.exception.EmailAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orquestra o autocadastro de um usuário do Kitchen Dashboard.
 *
 * Regra de negócio central: todo cadastro nasce PENDING e com role KITCHEN,
 * sem exceção — role nunca vem do request (RegisterRequest não tem esse
 * campo), então não existe forma de o cliente se autopromover a OWNER. A
 * aprovação para APPROVED é sempre uma decisão manual de um usuário OWNER
 * autenticado, feita dentro do próprio sistema (ver ApproveUserUseCase) —
 * não depende mais de nenhuma notificação externa. Este Use Case só sabe
 * criar a conta em estado de espera.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse execute(RegisterRequest request) {
        // Normalização de e-mail (trim + lowercase) antes de checar
        // duplicidade e persistir — sem isso, "Maria@x.com" e "maria@x.com"
        // passariam como e-mails diferentes, furando a regra de unicidade
        // na prática mesmo com o índice único (que é case-sensitive por
        // padrão no MongoDB).
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.KITCHEN)
                .status(UserStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        User saved = userRepository.save(user);
        log.info("Novo cadastro no Kitchen Dashboard: {} — aguardando aprovação de um OWNER", saved.getEmail());

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