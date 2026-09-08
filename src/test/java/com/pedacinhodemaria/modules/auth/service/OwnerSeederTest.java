package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OwnerSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new OwnerSeeder(userRepository, passwordEncoder);
    }

    private void setField(String name, Object value) throws Exception {
        var field = OwnerSeeder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(seeder, value);
    }

    @Test
    void naoFazNadaSeJaExisteUmOwner() throws Exception {
        setField("ownerEmail", "dona@exemplo.com");
        setField("ownerPassword", "senhaForte123");
        when(userRepository.existsByRole(UserRole.OWNER)).thenReturn(true);

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void naoCriaOwnerSeVariaveisDeAmbienteNaoConfiguradas() throws Exception {
        setField("ownerEmail", "");
        setField("ownerPassword", "");
        when(userRepository.existsByRole(UserRole.OWNER)).thenReturn(false);

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void naoCriaOwnerSeSoEmailConfigurado() throws Exception {
        setField("ownerEmail", "dona@exemplo.com");
        setField("ownerPassword", "");
        when(userRepository.existsByRole(UserRole.OWNER)).thenReturn(false);

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void criaOwnerComBCryptQuandoNaoExisteEVariaveisConfiguradas() throws Exception {
        setField("ownerEmail", "Dona@Exemplo.com");
        setField("ownerPassword", "senhaForte123");
        setField("ownerName", "Maria");
        when(userRepository.existsByRole(UserRole.OWNER)).thenReturn(false);
        when(passwordEncoder.encode("senhaForte123")).thenReturn("$2a$10$hashSimulado");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        seeder.run(null);
        verify(userRepository).save(captor.capture());

        User owner = captor.getValue();
        assertThat(owner.getEmail()).isEqualTo("dona@exemplo.com"); // normalizado
        assertThat(owner.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(UserStatus.APPROVED); // OWNER já nasce APPROVED
        assertThat(owner.getPasswordHash()).isEqualTo("$2a$10$hashSimulado").isNotEqualTo("senhaForte123");
        assertThat(owner.getName()).isEqualTo("Maria");
    }
}