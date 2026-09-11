import { CONFIG } from './config.js';
import { qs, show, hide } from './utils/domHelpers.js';
import { fetchActiveOrders, updateOrderStatus, sendReadyWhatsAppMessage } from './api/ordersApi.js';
import { ColumnManager } from './modules/columnManager.js';
import { StompClient } from './modules/wsClient.js';
import { hasValidSession, clearSession, authHeader } from './auth/session.js';
import { initAuthGate } from './auth/authGate.js';

// Intervalo de verificação da automação de 35 minutos — não precisa ser tão
// frequente quanto o scheduler do backend (15s); o ganho de precisão de
// checar mais rápido que isso não compensa o custo de rodar a varredura toda
// hora numa tela que já recebe eventos via WebSocket para tudo o que é
// realmente urgente.
const AUTO_READY_CHECK_INTERVAL_MS = 30_000;

let columnManager;
let stompClient;

/**
 * NOVO (Fase 2B): ponto de entrada agora decide entre a tela de auth e a
 * área da cozinha, em vez de ir direto pro gate de início de turno. O board
 * em si (colunas, WebSocket, ações de pedido) continua exatamente como
 * era — só ganhou uma guarda na frente.
 */
function bootstrapApp() {
    if (hasValidSession()) {
        enterKitchenArea();
    } else {
        showAuthGate();
    }
}

function showAuthGate() {
    show(qs('#auth-gate'));
    hide(qs('#shift-gate'));
    hide(qs('#board'));
    initAuthGate(enterKitchenArea);
}

/** Chamado tanto na carga inicial (sessão já válida) quanto após login bem-sucedido. */
function enterKitchenArea() {
    hide(qs('#auth-gate'));
    show(qs('#shift-gate'));
    qs('#start-shift-button').addEventListener('click', startShift, { once: true });
    qs('#logout-button')?.addEventListener('click', handleLogout);
}

function handleLogout() {
    clearSession();
    // Recarregar é a forma mais simples de resetar todo o estado em memória
    // (columnManager, conexão STOMP) sem introduzir um mecanismo de reset
    // manual só para esse caso raro — condizente com "não introduzir
    // complexidade desnecessária" já pedido para o resto do projeto.
    window.location.reload();
}

async function startShift() {
    hide(qs('#shift-gate'));
    show(qs('#board'));

    columnManager = new ColumnManager(handleAdvance, handleNotifyReady);

    await loadInitialOrders();
    connectWebSocket();

    setInterval(() => columnManager.checkAutoReadyTransitions(), AUTO_READY_CHECK_INTERVAL_MS);
}

/**
 * Fase 3: /api/v1/kitchen/** também passou a exigir JWT — mesmo tratamento
 * de 401/403 que já existia só para whatsapp-ready-message (Fase 2B),
 * agora extraído pra um único lugar em vez de duplicado nos 3 pontos de
 * chamada.
 *
 * @returns {boolean} true se o erro era 401/403 e já foi tratado (quem
 * chamou não precisa fazer mais nada); false se é outro tipo de erro.
 */
function handleAuthError(err) {
    if (err.status === 401) {
        alert('Sua sessão expirou. Faça login novamente.');
        clearSession();
        window.location.reload();
        return true;
    }
    if (err.status === 403) {
        alert('Você não tem permissão para essa ação.');
        return true;
    }
    return false;
}

async function loadInitialOrders() {
    try {
        const orders = await fetchActiveOrders();
        columnManager.init(orders);
    } catch (err) {
        if (handleAuthError(err)) return;
        console.error('Falha ao carregar pedidos ativos:', err);
        qs('#board-error').textContent = 'Não foi possível carregar os pedidos. Recarregue a página.';
        show(qs('#board-error'));
    }
}

async function handleAdvance(orderCode, nextStatus) {
    try {
        await updateOrderStatus(orderCode, nextStatus);
        // O ticket não é movido aqui diretamente — o backend publica
        // ORDER_STATUS_CHANGED de volta em /topic/kitchen-orders, e o handler
        // do WebSocket abaixo move o ticket. Mesmo caminho para esta e para
        // qualquer outra tela de cozinha conectada, sem duplicar lógica.
    } catch (err) {
        if (handleAuthError(err)) return;
        console.error('Falha ao atualizar status:', err);
        alert('Não foi possível atualizar o pedido. Tente novamente.');
    }
}

/**
 * Chamado quando o funcionário clica no telefone do cliente no ticket —
 * dispara a mensagem de WhatsApp via backend. Protegido por JWT desde a
 * Fase 2A.
 */
async function handleNotifyReady(orderCode) {
    try {
        await sendReadyWhatsAppMessage(orderCode);
    } catch (err) {
        if (handleAuthError(err)) return;
        console.error('Falha ao enviar mensagem de WhatsApp:', err);
        alert('Não foi possível enviar a mensagem de WhatsApp. Tente novamente.');
    }
}

function connectWebSocket() {
    stompClient = new StompClient(CONFIG.WS_URL);

    stompClient.connect(
        () => {
            stompClient.subscribe('/topic/kitchen-orders', handleKitchenEvent);
        },
        (err) => console.error('Falha na conexão WebSocket:', err),
        authHeader,
    );
}

function handleKitchenEvent(message) {
    if (message.order) {
        columnManager.addOrder(message.order);
        return;
    }

    if (message.newStatus) {
        columnManager.moveOrder(message.orderCode, message.newStatus);
        return;
    }

    if (message.timerState) {
        // Só atualiza o badge de cor (verde/amarelo/vermelho) — o alerta
        // sonoro que existia aqui foi removido; a automação de 35 minutos em
        // checkAutoReadyTransitions() substitui a necessidade de chamar
        // atenção manualmente para pedidos atrasados.
        columnManager.updateTimerState(message.orderCode, message.timerState);
    }
}

document.addEventListener('DOMContentLoaded', bootstrapApp);