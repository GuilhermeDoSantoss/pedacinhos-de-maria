package com.pedacinhodemaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Cobre TODO caso de link de aprovação inválido: token inexistente,
 * expirado, já utilizado, ou usuário que não está mais PENDING (já foi
 * APPROVED/REJECTED por outro link). De propósito uma única exceção para
 * os quatro casos — ver ProcessUserApprovalUseCase — mesmo raciocínio
 * anti-enumeration usado no login: quem encontrar um link antigo não
 * descobre qual desses quatro casos é o real.
 */
public class InvalidApprovalTokenException extends BusinessException {

    public InvalidApprovalTokenException() {
        super("Link de aprovação inválido ou expirado");
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}