package com.pedacinhodemaria.modules.auth.controller;

import com.pedacinhodemaria.modules.auth.service.ProcessUserApprovalUseCase;
import com.pedacinhodemaria.shared.exception.InvalidApprovalTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único ponto de entrada acionado pela dona ao tocar em ACEITAR/REJEITAR no
 * WhatsApp. Devolve HTML simples (não JSON) porque quem abre o link é o
 * navegador do celular dela, não um cliente de API.
 *
 * GET por necessidade prática (um link clicável numa mensagem de texto não
 * consegue disparar POST) — mitigado pelo fato de cada token só funcionar
 * uma vez (ver ProcessUserApprovalUseCase): mesmo que o link seja
 * pré-carregado por algo automatizado (link preview, antivírus de
 * mensageiro etc.), a segunda tentativa real da dona já cairia em "link
 * inválido", então o efeito colateral do GET fica visível na hora, não
 * silencioso.
 *
 * Erros NÃO passam pelo GlobalExceptionHandler (que devolve JSON) — são
 * tratados aqui mesmo, porque o formato de resposta desta rota é
 * inteiramente diferente do resto da API.
 */
@RestController
@RequestMapping("/api/v1/auth/approvals")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Aprovação/rejeição de cadastro via link do WhatsApp")
public class UserApprovalController {

    private final ProcessUserApprovalUseCase processUserApprovalUseCase;

    @GetMapping(value = "/{token}/accept", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Acionado pelo link ACEITAR enviado à dona via WhatsApp")
    public ResponseEntity<String> accept(@PathVariable String token) {
        return process(token, ProcessUserApprovalUseCase.Decision.ACCEPT, "\u2705 Solicitação aprovada com sucesso.");
    }

    @GetMapping(value = "/{token}/reject", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Acionado pelo link REJEITAR enviado à dona via WhatsApp")
    public ResponseEntity<String> reject(@PathVariable String token) {
        return process(token, ProcessUserApprovalUseCase.Decision.REJECT, "\u274C Solicitação rejeitada.");
    }

    private ResponseEntity<String> process(String token, ProcessUserApprovalUseCase.Decision decision, String successMessage) {
        try {
            processUserApprovalUseCase.execute(token, decision);
            return ResponseEntity.ok(page(successMessage));
        } catch (InvalidApprovalTokenException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(page("\u26A0\uFE0F Link inválido ou expirado."));
        }
    }

    /** Página mínima, sem identidade visual do Dashboard (fica pra Fase 2B) — só confirmação, sem dado sensível, sem token. */
    private String page(String message) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Pedacinho de Maria</title>
                    <style>
                        body { font-family: sans-serif; display: flex; align-items: center; justify-content: center;
                               height: 100vh; margin: 0; background: #fafafa; text-align: center; padding: 1.5rem; }
                        p { font-size: 1.2rem; color: #333; }
                    </style>
                </head>
                <body>
                    <p>%s</p>
                </body>
                </html>""".formatted(message);
    }
}