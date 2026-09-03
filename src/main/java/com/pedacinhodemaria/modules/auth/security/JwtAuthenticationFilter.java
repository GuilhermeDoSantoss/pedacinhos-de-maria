package com.pedacinhodemaria.modules.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lê "Authorization: Bearer <token>", valida via JwtService e popula o
 * SecurityContext — sem nova consulta ao banco (as claims do próprio token
 * já carregam e-mail + role, suficiente para authorizeHttpRequests decidir).
 *
 * Token ausente/inválido/expirado: NÃO lança exceção aqui, só segue sem
 * autenticar. Quem decide se isso é um problema é o authorizeHttpRequests —
 * rota pública segue normal, rota protegida vira 401/403 pelo próprio
 * Spring Security. Este filtro não precisa saber qual regra vale pra qual rota.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String email = jwtService.extractEmail(claims);
                String role = jwtService.extractRole(claims);

                var authentication = new UsernamePasswordAuthenticationToken(
                        email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Não logamos o token nem fragmento dele — só a categoria
                // do erro (expirado, assinatura inválida, malformado etc.).
                log.debug("Bearer token rejeitado em {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}