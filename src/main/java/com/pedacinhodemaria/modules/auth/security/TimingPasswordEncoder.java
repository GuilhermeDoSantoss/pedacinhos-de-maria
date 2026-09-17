package com.pedacinhodemaria.modules.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Decorator de instrumentação TEMPORÁRIA em torno do PasswordEncoder real —
 * delega 100% do trabalho de encode/matches ao encoder decorado (o mesmo
 * BCryptPasswordEncoder já usado antes desta classe existir) e só mede a
 * duração de matches(), a única chamada que acontece durante o login
 * (encode() só roda no cadastro, RegisterUserUseCase). Não faz uma segunda
 * verificação de senha, não altera o resultado de matches(), não muda o
 * custo do BCrypt — é transparente para o DaoAuthenticationProvider que a
 * chama através do AuthenticationManager.
 *
 * Objetivo: isolar, nos logs, quanto tempo o login gasta especificamente na
 * verificação de senha, separado da consulta ao MongoDB (medida em
 * PedacinhoUserDetailsService) e da geração do JWT (medida em
 * AuthenticateUserUseCase).
 *
 * Nunca loga a senha em texto puro nem o hash — só a duração e o loginId
 * de correlação (um UUID curto, sem relação com o usuário) já presente no
 * MDC quando AuthenticateUserUseCase inicia o login.
 *
 * Remover este wrap (voltar a `new BCryptPasswordEncoder()` puro em
 * SecurityConfig) quando a investigação de performance do login for
 * concluída — não é uma peça permanente de arquitetura.
 */
@Slf4j
public class TimingPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;

    public TimingPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        long start = System.nanoTime();
        boolean result = delegate.matches(rawPassword, encodedPassword);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_BCRYPT loginId={} duration={}ms", MDC.get("loginId"), durationMs);
        return result;
    }
}