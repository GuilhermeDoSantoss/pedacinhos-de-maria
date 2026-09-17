package com.pedacinhodemaria.modules.auth.security;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
 *
 * INSTRUMENTAÇÃO TEMPORÁRIA (investigação de lentidão no login): mede
 * exclusivamente a duração de findByEmail() — nenhuma chamada adicional ao
 * Mongo foi criada, é a mesma e única consulta que já existia aqui. Uso
 * try/finally para capturar a duração mesmo no caminho em que o e-mail não
 * existe (UsernameNotFoundException) — precisamos saber se consultas que
 * "erram" também estão lentas, não só as que acham o usuário. loginId vem
 * do MDC, setado por AuthenticateUserUseCase antes de authenticate() ser
 * chamado — é um UUID curto sem qualquer relação com o usuário, não o
 * e-mail nem qualquer dado pessoal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PedacinhoUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        long start = System.nanoTime();
        Optional<User> found;
        try {
            found = userRepository.findByEmail(email);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("AUTH_USER_LOOKUP_DB loginId={} duration={}ms", MDC.get("loginId"), durationMs);
        }

        User user = found
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