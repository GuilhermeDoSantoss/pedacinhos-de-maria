package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Segurança HTTP da aplicação.
 *
 * O que esta classe garante:
 *  1. Rotas do cliente (menu, orders, ws) continuam públicas — cliente
 *     nunca faz login (decisão de produto inalterada).
 *  2. Cadastro e login são públicos por natureza (não existe usuário
 *     autenticado antes de se cadastrar/logar).
 *  3. /api/v1/kitchen/** continua público nesta fase (ver decisão logo
 *     abaixo) — exceto o único endpoint já protegido por JWT+role.
 *  4. CSRF desabilitado — API stateless, sem sessão de cookie.
 *  5. Headers de segurança básicos continuam ativos.
 *  6. Toda rota que ainda não existe fica bloqueada por padrão (denyAll).
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    // NOVO (Fase 2A). Injetado por construtor (o resto da classe usa
    // @Value em campo, mas isso não impede injeção de bean por
    // construtor no mesmo @Configuration).
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

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
                        // NOVO (Fase 2A): "pedido pronto" é acionado pelo Dashboard da
                        // cozinha, não pelo cliente (ver javadoc de
                        // OrderController.sendReadyWhatsAppMessage) — por isso este
                        // matcher específico vem ANTES da regra geral de
                        // /api/v1/orders/** logo abaixo. Spring Security avalia na
                        // ordem declarada; a primeira regra que casar vence.
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/whatsapp-ready-message").hasAnyRole("KITCHEN", "OWNER")
                        // Cliente: criar pedido, consultar o próprio pedido, política de horário.
                        .requestMatchers("/api/v1/orders/**").permitAll()
                        // Decisão confirmada: /kitchen/** continua público por ora (o
                        // próprio KitchenOrderController já documentava isso como
                        // decisão de produto) — protegê-lo agora quebraria o Dashboard
                        // em produção, já que a Fase 2B (frontend com login) ainda não
                        // existe. Único endpoint de cozinha protegido nesta fase é
                        // whatsapp-ready-message acima, que já tinha sido sinalizado
                        // no próprio código como "o primeiro a proteger".
                        .requestMatchers("/api/v1/kitchen/**").permitAll()
                        // Sem isso, POST /api/v1/auth/register caía no anyRequest().denyAll()
                        // abaixo e retornava 403 antes mesmo de chegar no AuthController.
                        // Restrito a POST de propósito: não há motivo pra GET/PUT/DELETE.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        // NOVO: login é público pela mesma razão do
                        // cadastro — não existe usuário autenticado antes de logar.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // NOVO: administração de usuários — exclusivo de OWNER
                        // autenticado. Vem antes do denyAll final, e não há
                        // nenhum matcher mais genérico que /admin/** que precise
                        // vir antes dele (nenhuma outra regra usa esse prefixo).
                        .requestMatchers("/api/v1/admin/**").hasRole("OWNER")
                        .requestMatchers("/ws/**", "/ws-sockjs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .anyRequest().denyAll()
                )
                // NOVO (Fase 2A): lê e valida o Bearer token antes do filtro padrão
                // de autenticação por usuário/senha do Spring Security.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * NOVO (Fase 2A). Expõe o AuthenticationManager oficial do Spring
     * Security (mecanismo pedido explicitamente, não implementação
     * artesanal) — o Spring Boot o autoconfigura a partir do
     * PedacinhoUserDetailsService e do PasswordEncoder abaixo, ambos já
     * existentes; nenhum AuthenticationProvider foi construído manualmente.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
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