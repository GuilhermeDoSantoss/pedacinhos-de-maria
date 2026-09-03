package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.dto.LoginRequest;
import com.pedacinhodemaria.modules.auth.dto.TokenResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Orquestra o login. A verificação de senha (BCrypt) e de status
 * (PENDING/REJECTED bloqueados) NÃO acontece aqui — é inteiramente
 * delegada ao AuthenticationManager oficial do Spring Security
 * (DaoAuthenticationProvider, autoconfigurado a partir de
 * PedacinhoUserDetailsService + o PasswordEncoder já existente em
 * SecurityConfig). Qualquer falha vira AuthenticationException
 * (BadCredentialsException senha errada, DisabledException status!=APPROVED,
 * UsernameNotFoundException e-mail inexistente) — todas já capturadas pelo
 * GlobalExceptionHandler existente com a mesma resposta 401 genérica. Este
 * Use Case nunca precisa saber qual delas ocorreu.
 */
@Service
@RequiredArgsConstructor
public class AuthenticateUserUseCase {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public TokenResponse execute(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        // Chegou aqui só se autenticação passou (senha certa + APPROVED).
        // Busca o User de novo para pegar a role tipada (UserRole), em vez
        // de depender da string crua da GrantedAuthority.
        User user = userRepository.findByEmail(normalizedEmail).orElseThrow();

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return TokenResponse.bearer(token, jwtService.getExpirationSeconds());
    }
}