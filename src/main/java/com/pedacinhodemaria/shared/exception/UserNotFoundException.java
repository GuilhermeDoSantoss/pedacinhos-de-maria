package com.pedacinhodemaria.shared.exception;

import org.springframework.http.HttpStatus;

/** Lançada quando um OWNER tenta aprovar/rejeitar um id de usuário que não existe. */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String userId) {
        super("Usuário não encontrado: " + userId);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}