package com.pedacinhodemaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Lançada quando o e-mail informado no cadastro já pertence a outro
 * usuário. 409 Conflict (não 400) — o request em si é sintaticamente
 * válido, o problema é o estado atual do recurso (e-mail já existe).
 */
public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String email) {
        super("Já existe uma conta cadastrada com o e-mail " + email);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}