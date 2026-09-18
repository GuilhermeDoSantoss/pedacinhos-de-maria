package com.pedacinhodemaria.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bloqueia TODA requisição real com 503 + Retry-After enquanto
 * TrafficReadinessGate ainda não estiver pronto — ou seja, enquanto
 * LoginWarmupRunner ainda não terminou de aquecer Mongo, Spring
 * Security/BCrypt e JWT.
 *
 * Três categorias de requisição atravessam o gate mesmo fechado:
 *  1. /actuator/** — o health check precisa continuar respondendo com o
 *     status real da aplicação.
 *  2. GET /internal/keep-alive — precisa responder assim que o Tomcat
 *     aceitar conexões, sem esperar o warm-up interno terminar; seu único
 *     propósito é sinalizar atividade para o Render, não medir prontidão
 *     do login.
 *  3. Requisições OPTIONS — são o handshake de CORS preflight do
 *     navegador, nunca chegam a executar lógica de negócio; bloqueá-las
 *     com 503 faz o navegador reportar erro de CORS em vez do 503 real,
 *     sem ganhar nenhuma segurança adicional.
 *
 * Este filtro é registrado com a maior precedência possível
 * (Ordered.HIGHEST_PRECEDENCE, ver WarmupFilterConfig), diretamente no
 * container servlet — antes até da cadeia de filtros do Spring Security.
 * É essa posição, e não qualquer suposição sobre timing interno de
 * startup do Tomcat/ApplicationContext, que garante que nenhuma
 * requisição real alcance um controller antes do warm-up terminar.
 */
@Slf4j
@RequiredArgsConstructor
public class WarmupGateFilter extends OncePerRequestFilter {

    private static final String KEEP_ALIVE_PATH = "/internal/keep-alive";

    private final TrafficReadinessGate gate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        boolean isPreflight = "OPTIONS".equalsIgnoreCase(request.getMethod());
        boolean isExempt = request.getRequestURI().startsWith("/actuator")
                || request.getRequestURI().equals(KEEP_ALIVE_PATH)
                || isPreflight;

        if (isExempt) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!gate.isReady()) {
            log.info("AUTH_READINESS_REFUSING path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "5");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":503,\"error\":\"Service Unavailable\",\"message\":\"Aplicação inicializando, tente novamente em instantes.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}