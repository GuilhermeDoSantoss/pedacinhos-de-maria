package com.pedacinhodemaria.modules.auth.security;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ponte entre User (domínio) e UserDetails (contrato do Spring Security).
 *
 * enabled = status==APPROVED é a peça central: é o que faz o
 * DaoAuthenticationProvider oficial (autoconfigurado pelo Spring Boot a
 * partir deste bean + do PasswordEncoder já existente em SecurityConfig)
 * lançar DisabledException sozinho para PENDING/REJECTED — sem esta classe
 * ou o AuthenticateUserUseCase precisarem verificar status manualmente.
 * DisabledException é subclasse de AuthenticationException, então cai
 * direto no handler genérico que já existe em GlobalExceptionHandler,
 * devolvendo a mesma mensagem tanto pra PENDING quanto pra REJECTED —
 * evita revelar em qual dos dois estados o cadastro está.
 */
@Service
@RequiredArgsConstructor
public class PedacinhoUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                // Mensagem genérica de propósito — não revela se o e-mail existe
                // (mesmo raciocínio anti-enumeration do resto da Fase 2).
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(user.getStatus() != UserStatus.APPROVED)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}