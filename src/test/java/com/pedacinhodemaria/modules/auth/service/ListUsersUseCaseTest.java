package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ListUsersUseCase useCase;

    private User buildUser(String email, UserStatus status) {
        return User.builder()
                .id(email)
                .name(email)
                .email(email)
                .role(UserRole.KITCHEN)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void statusNuloListaTodosMaisRecentesPrimeiro() {
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(
                List.of(buildUser("a@x.com", UserStatus.PENDING), buildUser("b@x.com", UserStatus.APPROVED)));

        List<UserResponse> result = useCase.execute(null);

        assertThat(result).hasSize(2);
        verify(userRepository).findAllByOrderByCreatedAtDesc();
        verify(userRepository, never()).findByStatusOrderByCreatedAtDesc(any());
    }

    @Test
    void statusPendingFiltraSomentePendentes() {
        when(userRepository.findByStatusOrderByCreatedAtDesc(UserStatus.PENDING))
                .thenReturn(List.of(buildUser("a@x.com", UserStatus.PENDING)));

        List<UserResponse> result = useCase.execute(UserStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(UserStatus.PENDING);
        verify(userRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void respostaNuncaExpoePasswordHash() {
        // Estrutural: UserResponse (record) simplesmente não declara esse
        // componente — o teste documenta essa garantia de forma explícita.
        when(userRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(buildUser("a@x.com", UserStatus.PENDING)));

        List<UserResponse> result = useCase.execute(null);

        assertThat(result.get(0).getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("passwordHash");
    }
}