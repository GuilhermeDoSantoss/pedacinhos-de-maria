import { CONFIG } from '../config.js';
import { authHeader } from '../auth/session.js';

/**
 * Fase 3: /api/v1/kitchen/** deixou de ser público — precisa de JWT com
 * role KITCHEN/OWNER (ver SecurityConfig). `.status` no erro segue o mesmo
 * padrão já usado em sendReadyWhatsAppMessage, pra dashboard.js tratar
 * 401/403 de forma uniforme nos três endpoints.
 */
export async function fetchActiveOrders() {
    const response = await fetch(`${CONFIG.API_BASE_URL}/kitchen/orders`, {
        headers: { ...authHeader() },
    });

    if (!response.ok) {
        const error = new Error(`Falha ao carregar pedidos (status ${response.status})`);
        error.status = response.status;
        throw error;
    }

    return response.json();
}

/** Também protegido na Fase 3 — mesma role exigida acima. */
export async function updateOrderStatus(orderCode, newStatus) {
    const response = await fetch(`${CONFIG.API_BASE_URL}/kitchen/orders/${orderCode}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...authHeader() },
        body: JSON.stringify({ newStatus }),
    });

    if (!response.ok) {
        const apiError = await response.json().catch(() => null);
        const error = new Error(apiError?.message || 'Falha ao atualizar status do pedido');
        error.status = response.status;
        throw error;
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