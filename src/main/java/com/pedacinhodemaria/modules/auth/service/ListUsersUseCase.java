package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Alimenta a tela "Todos os usuários" / "Solicitações de acesso" do painel
 * administrativo. Reaproveita UserResponse (já usado pela resposta de
 * cadastro) — não há necessidade de um DTO administrativo separado, pois o
 * mesmo subconjunto de campos (nunca passwordHash) serve aos dois casos.
 */
@Service
@RequiredArgsConstructor
public class ListUsersUseCase {

    private final UserRepository userRepository;

    /** status == null → todos os usuários, mais recentes primeiro. */
    public List<UserResponse> execute(UserStatus status) {
        List<User> users = status == null
                ? userRepository.findAllByOrderByCreatedAtDesc()
                : userRepository.findByStatusOrderByCreatedAtDesc(status);

        return users.stream().map(this::toResponse).toList();
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