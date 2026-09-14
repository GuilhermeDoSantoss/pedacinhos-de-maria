import { qs, show, hide } from './utils/domHelpers.js';
import { hasValidSession, clearSession } from './session.js';
import { initAdminGate } from './adminGate.js';
import { initUsersView } from './usersView.js';

function bootstrap() {
    if (hasValidSession()) {
        enterPanel();
    } else {
        showLoginGate();
    }
}

function showLoginGate(message) {
    show(qs('#login-gate'));
    hide(qs('#panel'));

    if (message) {
        const errorEl = qs('#login-error');
        errorEl.textContent = message;
        show(errorEl);
    }

    initAdminGate(enterPanel);
}

/**
 * Chamado após login bem-sucedido OU na carga inicial com sessão já salva.
 * O login em si (POST /auth/login) NÃO verifica role — uma conta KITCHEN
 * também recebe um JWT válido. A real verificação de "isto é um OWNER" só
 * acontece aqui, na primeira chamada real a um endpoint /admin/** — se vier
 * 403, a conta é válida mas não tem permissão, e tratamos como tal.
 */
function enterPanel() {
    hide(qs('#login-gate'));
    show(qs('#panel'));
    qs('#logout-button').addEventListener('click', handleLogout, { once: true });

    initUsersView(handleAuthError);
}

function handleAuthError(status) {
    clearSession();

    if (status === 403) {
        showLoginGate('Esta conta não possui permissão de administrador (ROLE_OWNER).');
    } else {
        showLoginGate('Sua sessão expirou. Faça login novamente.');
    }
}

function handleLogout() {
    clearSession();
    window.location.reload();
}

document.addEventListener('DOMContentLoaded', bootstrap);