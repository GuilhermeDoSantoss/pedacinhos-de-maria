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
 * Bloqueia TODA requisição (exceto /actuator/**) com 503 + Retry-After
 * enquanto TrafficReadinessGate ainda não estiver pronto — ou seja,
 * enquanto LoginWarmupRunner ainda não terminou de aquecer Mongo,
 * Spring Security/BCrypt e JWT.
 *
 * /actuator/** é sempre deixado passar, mesmo com o gate fechado, para
 * que /actuator/health continue respondendo com o status real da
 * aplicação — bloquear o próprio health check seria contraproducente
 * tanto para o keep-alive quanto para o healthCheckPath do render.yaml.
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

    private final TrafficReadinessGate gate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator")) {
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