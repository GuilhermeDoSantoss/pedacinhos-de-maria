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

/** Mesmo raciocínio de ApproveUserUseCase, transição inversa: PENDING → REJECTED. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RejectUserUseCase {

    private final UserRepository userRepository;

    public UserResponse execute(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserAlreadyProcessedException(userId, user.getStatus());
        }

        user.setStatus(UserStatus.REJECTED);
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        log.info("Usuário {} rejeitado — login permanece negado", saved.getEmail());

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