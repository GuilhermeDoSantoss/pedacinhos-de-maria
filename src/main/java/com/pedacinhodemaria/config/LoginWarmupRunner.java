package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.slf4j.MDC;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Aquece, uma única vez logo após o startup, os subsistemas cujo primeiro
 * uso real (Mongo/Atlas, Spring Security/BCrypt, JJWT) se mostrou muito
 * mais lento que os usos seguintes.
 *
 *  a versão anterior deste componente usava só AvailabilityChangeEvent/ReadinessState para tentar
 * impedir tráfego durante o warm-up. Logs reais provaram que isso NÃO
 * impede o Tomcat/DispatcherServlet de processar requisições reais —
 * ReadinessState é um sinal informativo, consumido só por
 * /actuator/health/readiness, para orquestradores externos decidirem se
 * roteiam tráfego; não é um mecanismo de bloqueio interno. A garantia
 * real agora vem de TrafficReadinessGate + WarmupGateFilter (ver javadoc
 * de ambos) — o AvailabilityChangeEvent continua sendo publicado aqui só
 * para manter /actuator/health/readiness relatando a verdade, não porque
 * ele impeça tráfego sozinho.
 *
 * Não toca em nenhum usuário real, não gera um JWT que seria aceito como
 * credencial válida (ver JwtService.warmUp() — nasce já expirado), não
 * altera nenhuma coleção do Mongo, e nunca loga senha, hash, token ou
 * segredo.
 *
 * Falha em qualquer etapa do warm-up NUNCA impede a aplicação de ficar
 * pronta — cada etapa é isolada e o gate é liberado no finally do método
 * inteiro, garantindo que a aplicação nunca fique permanentemente
 * REFUSING_TRAFFIC mesmo se algo inesperado quebrar durante o warm-up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginWarmupRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final String WARMUP_LOGIN_ID = "warmup";

    private final MongoTemplate mongoTemplate;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TrafficReadinessGate trafficReadinessGate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // trafficReadinessGate já nasce fechado (ver TrafficReadinessGate) —
        // esta linha só deixa explícito no log que o warm-up está
        // começando com o gate fechado, não é ela quem fecha o gate.
        log.info("AUTH_READINESS_REFUSING reason=warmup_starting");
        MDC.put("loginId", WARMUP_LOGIN_ID);
        long totalStart = System.nanoTime();
        log.info("AUTH_WARMUP_START");

        try {
            runStep("Mongo", this::warmUpMongo);
            runStep("Security", this::warmUpAuthenticationAndBCrypt);
            runStep("Jwt", this::warmUpJwt);

            long totalDurationMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info("AUTH_WARMUP_SUCCESS total={}ms", totalDurationMs);
        } catch (Exception unexpected) {
            // runStep já isola a falha de cada etapa individualmente; este
            // catch é uma rede de segurança adicional para qualquer erro
            // fora das etapas em si — nunca deve impedir a liberação do
            // gate no finally abaixo.
            log.warn("AUTH_WARMUP_FAILURE step=unexpected reason={}", unexpected.getClass().getSimpleName());
        } finally {
            MDC.remove("loginId");
            // GARANTIA CRÍTICA: o gate SEMPRE abre aqui, warm-up tendo
            // funcionado ou não — a aplicação nunca fica permanentemente
            // recusando tráfego por causa de uma falha no warm-up.
            trafficReadinessGate.markReady();
            log.info("AUTH_READINESS_ACCEPTING");
            AvailabilityChangeEvent.publish(event.getApplicationContext(), ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    private void runStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (Exception ex) {
            log.warn("AUTH_WARMUP_FAILURE step={} reason={}", stepName, ex.getClass().getSimpleName());
        }
    }

    private void warmUpMongo() {
        long start = System.nanoTime();
        mongoTemplate.getDb().runCommand(new Document("ping", 1));
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_WARMUP_MONGO duration={}ms", durationMs);
    }

    private void warmUpAuthenticationAndBCrypt() {
        long start = System.nanoTime();
        String syntheticEmail = "warmup-" + UUID.randomUUID() + "@internal.invalid";
        String syntheticPassword = UUID.randomUUID().toString();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(syntheticEmail, syntheticPassword));
        } catch (AuthenticationException expected) {
            // Resultado esperado e correto — e-mail sintético nunca existe.
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_WARMUP_SECURITY duration={}ms", durationMs);
    }

    private void warmUpJwt() {
        long start = System.nanoTime();
        jwtService.warmUp();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_WARMUP_JWT duration={}ms", durationMs);
    }
}