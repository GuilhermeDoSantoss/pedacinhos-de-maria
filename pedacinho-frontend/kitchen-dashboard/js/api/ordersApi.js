import { CONFIG } from '../config.js';
import { authHeader } from '../auth/session.js';

/**
 * Continua sem autenticação — decisão de produto preservada da Fase 2A:
 * /api/v1/kitchen/** segue público nesta fase (ver SecurityConfig e o
 * próprio KitchenOrderController, que já documentava isso).
 */
export async function fetchActiveOrders() {
    const response = await fetch(`${CONFIG.API_BASE_URL}/kitchen/orders`);
    if (!response.ok) {
        throw new Error(`Falha ao carregar pedidos (status ${response.status})`);
    }
    return response.json();
}

/** Também continua público — mesma decisão acima. */
export async function updateOrderStatus(orderCode, newStatus) {
    const response = await fetch(`${CONFIG.API_BASE_URL}/kitchen/orders/${orderCode}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newStatus }),
    });

    if (!response.ok) {
        const apiError = await response.json();
        throw new Error(apiError.message || 'Falha ao atualizar status do pedido');
    }

    return response.json();
}

/**
 * NOVO (Fase 2B): único endpoint do dashboard que exige JWT (ver
 * SecurityConfig, Fase 2A — POST /api/v1/orders/{orderCode}/whatsapp-ready-message
 * requer role KITCHEN/OWNER). O erro lançado carrega `.status` para
 * dashboard.js decidir o tratamento de 401/403.
 */
export async function sendReadyWhatsAppMessage(orderCode) {
    const response = await fetch(
        `${CONFIG.API_BASE_URL}/orders/${encodeURIComponent(orderCode)}/whatsapp-ready-message`,
        {
            method: 'POST',
            headers: { ...authHeader() },
        }
    );

    if (!response.ok) {
        const error = new Error('Erro ao enviar mensagem');
        error.status = response.status;
        throw error;
    }
}