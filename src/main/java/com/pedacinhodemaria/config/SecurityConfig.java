package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.security.JwtAuthenticationFilter;
import com.pedacinhodemaria.modules.auth.security.TimingPasswordEncoder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
 *  3. /api/v1/kitchen/** exige JWT + role KITCHEN ou OWNER (Fase 3) —
 *     mesma exigência do endpoint de whatsapp-ready-message.
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
                .anonymous(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Autenticação necessária\"}");
                        })
                )
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/whatsapp-ready-message").hasAnyRole("KITCHEN", "OWNER")
                        .requestMatchers("/api/v1/orders/**").permitAll()
                        .requestMatchers("/api/v1/kitchen/**").hasAnyRole("KITCHEN", "OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("OWNER")
                        .requestMatchers("/ws/**", "/ws-sockjs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // NOVO: endpoint dedicado de keep-alive (ver KeepAliveController) —
                        // separado de /actuator/health de propósito (não depende do
                        // MongoHealthIndicator). Só GET, só esse path exato — mesma
                        // categoria de exposição pública que /actuator/health acima.
                        .requestMatchers(HttpMethod.GET, "/internal/keep-alive").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new TimingPasswordEncoder(new BCryptPasswordEncoder());
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}