package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.shared.exception.UserAlreadyProcessedException;
import com.pedacinhodemaria.shared.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApproveUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApproveUserUseCase useCase;

    private User pendingUser;

    @BeforeEach
    void setUp() {
        pendingUser = User.builder()
                .id("1")
                .name("João Silva")
                .email("joao@exemplo.com")
                .role(UserRole.KITCHEN)
                .status(UserStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void deveAprovarUsuarioPending() {
        when(userRepository.findById("1")).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = useCase.execute("1");

        assertThat(response.status()).isEqualTo(UserStatus.APPROVED);
    }

    @Test
    void devePersistirATransicao() {
        when(userRepository.findById("1")).thenReturn(Optional.of(pendingUser));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute("1");

        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.APPROVED);
    }

    @Test
    void deveLancarUserNotFoundExceptionParaIdInexistente() {
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("999")).isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void naoDevePermitirAprovarUsuarioJaApproved() {
        pendingUser.setStatus(UserStatus.APPROVED);
        when(userRepository.findById("1")).thenReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> useCase.execute("1")).isInstanceOf(UserAlreadyProcessedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void naoDevePermitirAprovarUsuarioJaRejected() {
        pendingUser.setStatus(UserStatus.REJECTED);
        when(userRepository.findById("1")).thenReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> useCase.execute("1")).isInstanceOf(UserAlreadyProcessedException.class);

        verify(userRepository, never()).save(any());
    }
}