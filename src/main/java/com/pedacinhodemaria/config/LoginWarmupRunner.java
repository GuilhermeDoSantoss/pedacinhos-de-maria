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
 * mais lento que os usos seguintes — ver instrumentação AUTH_* e a
 * investigação de lentidão do primeiro login após um restart do processo
 * (Mongo: 710-797ms → 123-196ms; BCrypt: 1101-1202ms → 458-689ms;
 * AuthenticationManager: 3297-3303ms → 582-826ms; JWT: 1803-2299ms → 0ms).
 *
 * Não toca em nenhum usuário real, não gera um JWT que seria aceito como
 * credencial válida (ver JwtService.warmUp() — nasce já expirado), não
 * altera nenhuma coleção do Mongo, e nunca loga senha, hash, token ou
 * segredo.
 *
 * Enquanto o warm-up roda, o estado de readiness da aplicação é mantido em
 * REFUSING_TRAFFIC via ApplicationAvailability (API oficial do Spring
 * Boot). Como a aplicação roda em Docker, o Spring Boot 3.1+ detecta esse
 * ambiente automaticamente e expõe os grupos "liveness"/"readiness" em
 * /actuator/health — o mesmo endpoint usado como healthCheckPath no
 * render.yaml e pelo keep-alive. Durante o warm-up, /actuator/health passa
 * a reportar a aplicação como não pronta; se o Render de fato aguardar
 * esse sinal antes de encaminhar tráfego (comportamento a confirmar em
 * produção — documentado para deploys, não garantido para o "acordar" de
 * uma instância ociosa), o usuário deixa de pagar o custo do aquecimento
 * dentro do próprio login.
 *
 * Falha em qualquer etapa do warm-up NUNCA impede a aplicação de ficar
 * pronta — cada etapa é isolada (uma falhando não impede as demais) e o
 * estado sempre volta para ACCEPTING_TRAFFIC ao final, mesmo que
 * Mongo/Auth/JWT falhem aqui. Este componente é uma otimização, nunca um
 * requisito para a aplicação funcionar: se o warm-up falhar por completo,
 * o primeiro login real simplesmente paga o custo normalmente, como pagava
 * antes deste componente existir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginWarmupRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final String WARMUP_LOGIN_ID = "warmup";

    private final MongoTemplate mongoTemplate;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        AvailabilityChangeEvent.publish(event.getApplicationContext(), ReadinessState.REFUSING_TRAFFIC);
        MDC.put("loginId", WARMUP_LOGIN_ID);
        long totalStart = System.nanoTime();
        log.info("AUTH_WARMUP_START");

        runStep("Mongo", this::warmUpMongo);
        runStep("Security", this::warmUpAuthenticationAndBCrypt);
        runStep("Jwt", this::warmUpJwt);

        long totalDurationMs = (System.nanoTime() - totalStart) / 1_000_000;
        log.info("AUTH_WARMUP_SUCCESS total={}ms", totalDurationMs);

        MDC.remove("loginId");
        AvailabilityChangeEvent.publish(event.getApplicationContext(), ReadinessState.ACCEPTING_TRAFFIC);
    }

    private void runStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (Exception ex) {
            log.warn("AUTH_WARMUP_FAILURE step={} reason={}", stepName, ex.getClass().getSimpleName());
        }
    }

    /**
     * Comando "ping" nativo do MongoDB — não lê nem escreve nenhuma
     * coleção, não depende de nenhum documento existir, é a forma padrão
     * recomendada de verificar/estabelecer conectividade com um cluster.
     * Força aqui, no startup, o custo de resolução DNS do mongodb+srv://,
     * handshake TLS e autenticação da primeira conexão do pool.
     */
    private void warmUpMongo() {
        long start = System.nanoTime();
        mongoTemplate.getDb().runCommand(new Document("ping", 1));
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_WARMUP_MONGO duration={}ms", durationMs);
    }

    /**
     * Uma tentativa de autenticação com credenciais sintéticas que NUNCA
     * correspondem a um usuário real (e-mail sob o domínio reservado
     * .invalid, RFC 2606; senha aleatória descartada). O resultado
     * esperado e correto é sempre falha (AuthenticationException) — não é
     * um erro de warm-up, nunca é logado como problema.
     *
     * O valor desta chamada: o Spring Security
     * (AbstractUserDetailsAuthenticationProvider, classe-mãe do
     * DaoAuthenticationProvider usado por trás do AuthenticationManager)
     * aplica uma proteção conhecida contra timing attack de enumeração de
     * usuário — quando o e-mail não existe, ele ainda assim executa
     * passwordEncoder.encode() (uma vez, cacheado) e
     * passwordEncoder.matches() contra um hash interno, exatamente para
     * igualar o tempo de resposta ao de uma tentativa com e-mail
     * existente. Isso significa que esta chamada aquece de forma real, não
     * simulada: a consulta ao Mongo (via PedacinhoUserDetailsService,
     * reaproveitando a conexão já aberta por warmUpMongo), o BCrypt (via
     * TimingPasswordEncoder — os logs AUTH_USER_LOOKUP_DB e AUTH_BCRYPT já
     * existentes aparecem aqui com loginId=warmup, o mesmo nome de sempre,
     * só marcados para não se confundirem com uma tentativa de usuário
     * real) e o carregamento de classes do próprio
     * ProviderManager/DaoAuthenticationProvider.
     *
     * Nenhum usuário real é consultado, nenhuma sessão é criada (API já é
     * stateless), nenhum JWT é gerado neste caminho — a execução sempre
     * termina em exceção antes de chegar a esse ponto do fluxo real.
     */
    private void warmUpAuthenticationAndBCrypt() {
        long start = System.nanoTime();
        String syntheticEmail = "warmup-" + UUID.randomUUID() + "@internal.invalid";
        String syntheticPassword = UUID.randomUUID().toString();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(syntheticEmail, syntheticPassword));
        } catch (AuthenticationException expected) {
            // Resultado esperado e correto — e-mail sintético nunca existe.
            // Qualquer subtipo de AuthenticationException é aceitável aqui;
            // o objetivo é exercitar o pipeline, não obter sucesso.
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_WARMUP_SECURITY duration={}ms", durationMs);
    }

    /** Ver JwtService.warmUp() — gera e descarta um token sintético já expirado. */
    private void warmUpJwt() {
        long start = System.nanoTime();
        jwtService.warmUp();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AUTH_WARMUP_JWT duration={}ms", durationMs);
    }
}