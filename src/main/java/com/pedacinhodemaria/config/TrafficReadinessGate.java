package com.pedacinhodemaria.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gate compartilhado que decide se a aplicação já está pronta para
 * processar requisições reais. Existe porque o ReadinessState do Spring
 * Boot Application Availability é um sinal PURAMENTE INFORMATIVO,
 * consumido só por /actuator/health/readiness para orquestradores
 * externos (Kubernetes, por exemplo) decidirem se roteiam tráfego — ele
 * não impede o próprio Tomcat/DispatcherServlet de processar requisições.
 * Os logs de produção provaram isso: um login real foi processado
 * inteiramente enquanto o estado interno estava REFUSING_TRAFFIC.
 *
 * Este componente, combinado com WarmupGateFilter, é o mecanismo que
 * realmente impede qualquer requisição de alcançar um controller antes do
 * warm-up terminar — não depende de nenhuma suposição sobre a ordem
 * interna de inicialização do Tomcat/ApplicationContext.
 *
 * Começa em "não pronto" desde a criação do bean — antes de qualquer
 * warm-up rodar — e só vira "pronto" quando LoginWarmupRunner chamar
 * markReady() no finally, tenha o warm-up funcionado ou falhado.
 */
@Component
public class TrafficReadinessGate {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    public void markReady() {
        ready.set(true);
    }
}