package com.pedacinhodemaria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Segurança HTTP da aplicação. Decisão de produto:
 * SEM autenticação — nem cliente nem cozinha fazem login. O
 * Kitchen Dashboard abre direto no board e conecta ao WebSocket sem token.
 *
 * O que esta classe garante:
 *  1. Todas as rotas de negócio (menu, orders, kitchen) são públicas.
 *  2. CSRF desabilitado — API stateless, sem sessão de cookie.
 *  3. Headers de segurança básicos (anti-clickjacking, anti-MIME-sniffing)
 *     continuam ativos — não têm custo e não dependem de autenticação.
 *  4. Toda rota que ainda não existe fica bloqueada por padrão (denyAll).
 *
 * (auth): a rota de auto-cadastro é pública por natureza — não existe
 * usuário autenticado antes de se cadastrar. Login/JWT permanecem fora do
 * escopo desta fase (ver RegisterUserUseCase); quando entrarem, as demais
 * rotas de negócio deixarão de ser permitAll — não antecipamos isso aqui.
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(contentTypeOptions -> {})
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/menu/**").permitAll()
                        .requestMatchers("/api/v1/side-dishes/**").permitAll()
                        .requestMatchers("/api/v1/extras/**").permitAll()
                        .requestMatchers("/api/v1/drinks/**").permitAll()
                        .requestMatchers("/api/v1/orders/**").permitAll()
                        .requestMatchers("/api/v1/kitchen/**").permitAll()
                        // NOVO (Fase 1): sem isso, POST /api/v1/auth/register caía no
                        // anyRequest().denyAll() abaixo e retornava 403 antes mesmo de
                        // chegar no AuthController — o cadastro nunca funcionou com a
                        // config anterior. Restrito a POST de propósito: não há motivo
                        // pra GET/PUT/DELETE existirem nesse path.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers("/ws/**", "/ws-sockjs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/**").permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }

    /**
     * NOVO (Fase 1). Não encontrado em nenhuma outra classe do projeto
     * (SecurityConfig, MongoIndexInitializer, User, UserRepository,
     * RegisterUserUseCase, AuthController, RegisterRequest, UserResponse,
     * EmailAlreadyExistsException — nenhuma define PasswordEncoder). Único
     * bean do tipo no projeto; RegisterUserUseCase o injeta por construtor.
     * Fica aqui por ser a classe @Configuration de segurança já existente —
     * não criei uma classe nova só para isso.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}