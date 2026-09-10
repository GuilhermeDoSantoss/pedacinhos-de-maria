package com.pedacinhodemaria.modules.auth.security;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Cobre diretamente a regra central que PENDING/REJECTED nunca conseguem
 * autenticar: enabled = status==APPROVED. Antes desta classe, essa regra só
 * era exercitada indiretamente por mocks em AuthenticateUserUseCaseTest,
 * que já assumiam o comportamento correto em vez de verificá-lo na fonte.
 */
@ExtendWith(MockitoExtension.class)
class PedacinhoUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PedacinhoUserDetailsService userDetailsService;

    private User buildUser(UserStatus status) {
        return User.builder()
                .id("1")
                .name("Maria")
                .email("maria@exemplo.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.KITCHEN)
                .status(status)
                .build();
    }

    @Test
    void usuarioApprovedEstaHabilitado() {
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(buildUser(UserStatus.APPROVED)));

        UserDetails details = userDetailsService.loadUserByUsername("maria@exemplo.com");

        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void usuarioPendingNaoEstaHabilitado() {
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(buildUser(UserStatus.PENDING)));

        UserDetails details = userDetailsService.loadUserByUsername("maria@exemplo.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void usuarioRejectedNaoEstaHabilitado() {
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(buildUser(UserStatus.REJECTED)));

        UserDetails details = userDetailsService.loadUserByUsername("maria@exemplo.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void emailInexistenteLancaUsernameNotFoundException() {
        when(userRepository.findByEmail("naoexiste@exemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("naoexiste@exemplo.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void authorityReflete_ROLE_MaisONomeDaRole() {
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(buildUser(UserStatus.APPROVED)));

        UserDetails details = userDetailsService.loadUserByUsername("maria@exemplo.com");

        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_KITCHEN");
    }

    @Test
    void passwordERaOHashArmazenadoNuncaTextoPuro() {
        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(buildUser(UserStatus.APPROVED)));

        UserDetails details = userDetailsService.loadUserByUsername("maria@exemplo.com");

        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
    }
}