package com.pedacinhodemaria.modules.order.service;

/**
 * Port da integração de WhatsApp. Inferido a partir do uso real em
 * SendOrderReadyWhatsAppMessageUseCase e WhatsAppCloudApiMessageSender (o
 * arquivo da interface em si não foi anexado nesta conversa) — a assinatura
 * abaixo é exatamente a que já é chamada nos dois arquivos existentes, não
 * uma estrutura nova inventada. Se o arquivo real tiver métodos adicionais
 * não usados por nenhum dos dois use cases, adicione-os aqui manualmente.
 */
public interface WhatsAppMessageSender {
    void sendMessage(String phoneNumber, String message);
}