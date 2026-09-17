package com.pedacinhodemaria.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra WarmupGateFilter com a maior precedência possível, diretamente
 * no container servlet — antes de qualquer filtro do Spring Security e
 * antes de qualquer outro filtro da aplicação. Ver javadoc de
 * WarmupGateFilter para o motivo desta posição ser a peça que realmente
 * garante exclusão mútua entre warm-up e login real.
 */
@Configuration
@RequiredArgsConstructor
public class WarmupFilterConfig {

    private final TrafficReadinessGate trafficReadinessGate;

    @Bean
    public FilterRegistrationBean<WarmupGateFilter> warmupGateFilterRegistration() {
        FilterRegistrationBean<WarmupGateFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new WarmupGateFilter(trafficReadinessGate));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}