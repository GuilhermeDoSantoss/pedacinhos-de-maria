package com.pedacinhodemaria.shared.exception;

import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import org.springframework.http.HttpStatus;

/**
 * Lançada quando um OWNER tenta aprovar/rejeitar um usuário que não está
 * mais PENDING — já foi processado antes (por outro clique, ou pela mesma
 * OWNER duas vezes). 409 Conflict: o request é válido, o problema é o
 * estado atual do recurso, mesmo raciocínio de EmailAlreadyExistsException.
 */
public class UserAlreadyProcessedException extends BusinessException {

    public UserAlreadyProcessedException(String userId, UserStatus currentStatus) {
        super("Usuário " + userId + " já foi processado anteriormente (status atual: " + currentStatus + ")");
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}