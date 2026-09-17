package com.pedacinhodemaria.modules.auth.service;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.dto.LoginRequest;
import com.pedacinhodemaria.modules.auth.dto.TokenResponse;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import com.pedacinhodemaria.modules.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
 *
 * INSTRUMENTAÇÃO TEMPORÁRIA (investigação de lentidão no login — não é
 * comportamento permanente, não é arquitetura definitiva): mede cada etapa
 * com System.nanoTime() e loga em INFO. Nenhuma senha, hash, JWT, secret,
 * URI ou header de Authorization é logado em nenhum ponto. Um loginId
 * (UUID curto, sem qualquer relação com o usuário) é colocado no MDC no
 * início e removido no finally, só para correlacionar estas linhas com
 * AUTH_USER_LOOKUP_DB (PedacinhoUserDetailsService) e AUTH_BCRYPT
 * (TimingPasswordEncoder) nos logs do Render.
 *
 * A regra de negócio, as exceções lançadas, o tipo de exceção propagada e
 * o retorno em caso de sucesso são idênticos aos de antes desta
 * instrumentação — cada bloco de medição só envolve uma chamada que já
 * existia, sem duplicá-la e sem alterar seu resultado. A segunda consulta
 * a findByEmail() abaixo (AUTH_USER_REFETCH) é a mesma que já existia
 * antes desta instrumentação — só foi medida, não removida, conforme
 * combinado: a decisão de eliminá-la fica para depois de medirmos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticateUserUseCase {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public TokenResponse execute(LoginRequest request) {
        String loginId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("loginId", loginId);
        long totalStart = System.nanoTime();

        try {
            String normalizedEmail = request.email().trim().toLowerCase();
            log.info("AUTH_LOGIN_START loginId={} email={}", loginId, maskEmail(normalizedEmail));

            long authStart = System.nanoTime();
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
            long authDurationMs = (System.nanoTime() - authStart) / 1_000_000;
            log.info("AUTH_AUTHENTICATION loginId={} duration={}ms", loginId, authDurationMs);

            // Chegou aqui só se autenticação passou (senha certa + APPROVED).
            // Busca o User de novo para pegar a role tipada (UserRole), em vez
            // de depender da string crua da GrantedAuthority. Chamada JÁ
            // EXISTENTE antes desta instrumentação — nenhuma consulta nova foi
            // criada, só medida. Ver nota da classe sobre eliminação futura.
            long refetchStart = System.nanoTime();
            User user = userRepository.findByEmail(normalizedEmail).orElseThrow();
            long refetchDurationMs = (System.nanoTime() - refetchStart) / 1_000_000;
            log.info("AUTH_USER_REFETCH loginId={} duration={}ms", loginId, refetchDurationMs);

            long jwtStart = System.nanoTime();
            String token = jwtService.generateToken(user.getEmail(), user.getRole());
            long jwtDurationMs = (System.nanoTime() - jwtStart) / 1_000_000;
            log.info("AUTH_JWT_GENERATION loginId={} duration={}ms", loginId, jwtDurationMs);

            long totalDurationMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info("AUTH_LOGIN_SUCCESS loginId={} total={}ms", loginId, totalDurationMs);

            return TokenResponse.bearer(token, jwtService.getExpirationSeconds());
        } catch (RuntimeException ex) {
            // Credenciais inválidas, conta PENDING/REJECTED, ou qualquer outra
            // falha — relançada IDÊNTICA (mesmo tipo, mesma mensagem), sem
            // alterar o status HTTP resultante. Só medimos e logamos antes de
            // propagar, porque "às vezes o login falha" também precisa de
            // tempo medido, não só o caminho de sucesso.
            long totalDurationMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info("AUTH_LOGIN_FAILURE loginId={} total={}ms reason={}",
                    loginId, totalDurationMs, ex.getClass().getSimpleName());
            throw ex;
        } finally {
            MDC.remove("loginId");
        }
    }

    /**
     * g***@***.com — nunca o e-mail completo nos logs desta classe. Mantém
     * só o primeiro caractere da parte local e o TLD, suficiente para um
     * humano reconhecer "foi uma tentativa do Fulano" durante uma
     * investigação, sem expor o e-mail inteiro em log de produção.
     */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String firstChar = local.isEmpty() ? "*" : local.substring(0, 1);
        int lastDot = domain.lastIndexOf('.');
        String tld = lastDot >= 0 ? domain.substring(lastDot) : "";
        return firstChar + "***@***" + tld;
    }
}