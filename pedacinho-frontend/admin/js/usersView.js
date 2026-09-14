import { createElement, formatDate, qs, show, hide } from './utils/domHelpers.js';
import { fetchUsers, approveUser, rejectUser } from './usersApi.js';

const STATUS_LABELS = { PENDING: 'Pendente', APPROVED: 'Aprovado', REJECTED: 'Rejeitado' };

let currentFilter = null; // null = "Todos"

/** onAuthError(status) é chamado para 401/403 na PRIMEIRA carga — quem decide o que fazer (logout, mensagem) é app.js. */
export function initUsersView(onAuthError) {
    for (const tab of document.querySelectorAll('.filter-tab')) {
        tab.addEventListener('click', () => {
            currentFilter = tab.dataset.status || null;
            for (const t of document.querySelectorAll('.filter-tab')) {
                t.classList.toggle('filter-tab--active', t === tab);
            }
            loadUsers(onAuthError);
        });
    }

    return loadUsers(onAuthError);
}

export async function loadUsers(onAuthError) {
    const listEl = qs('#users-list');
    const emptyEl = qs('#empty-message');
    const errorEl = qs('#panel-error');
    hide(errorEl);

    try {
        const users = await fetchUsers(currentFilter);
        listEl.innerHTML = '';

        if (users.length === 0) {
            show(emptyEl);
        } else {
            hide(emptyEl);
            for (const user of users) {
                listEl.appendChild(renderUserCard(user, onAuthError));
            }
        }
    } catch (err) {
        if (err.status === 401 || err.status === 403) {
            onAuthError(err.status);
            return;
        }
        errorEl.textContent = genericErrorMessage(err);
        show(errorEl);
    }
}

function renderUserCard(user, onAuthError) {
    const showActions = user.status === 'PENDING';

    return createElement('article', { className: 'user-card' }, [
        createElement('div', { className: 'user-card__info' }, [
            createElement('h3', { className: 'user-card__name' }, [user.name]),
            createElement('p', { className: 'user-card__email' }, [user.email]),
            createElement('p', { className: 'user-card__date' }, [`Cadastrado em ${formatDate(user.createdAt)}`]),
        ]),
        createElement('span', { className: `status-badge status-badge--${user.status.toLowerCase()}` }, [STATUS_LABELS[user.status]]),
        showActions
            ? createElement('div', { className: 'user-card__actions' }, [
                createElement('button', {
                    className: 'btn btn--approve',
                    type: 'button',
                    onClick: (event) => handleApprove(user.id, event.currentTarget, onAuthError),
                }, ['Aprovar']),
                createElement('button', {
                    className: 'btn btn--reject',
                    type: 'button',
                    onClick: (event) => handleReject(user.id, event.currentTarget, onAuthError),
                }, ['Rejeitar']),
            ])
            : createElement('span', {}, []),
    ]);
}

async function handleApprove(id, button, onAuthError) {
    await runAction(button, 'Aprovando...', () => approveUser(id), onAuthError);
}

async function handleReject(id, button, onAuthError) {
    if (!window.confirm('Tem certeza que deseja rejeitar este cadastro? O usuário não poderá fazer login.')) {
        return;
    }
    await runAction(button, 'Rejeitando...', () => rejectUser(id), onAuthError);
}

/** Desabilita o botão clicado (evita duplo clique) e recarrega a lista após a ação, sem exigir refresh manual da página. */
async function runAction(button, loadingLabel, action, onAuthError) {
    const originalLabel = button.textContent;
    button.disabled = true;
    button.textContent = loadingLabel;

    const errorEl = qs('#panel-error');
    hide(errorEl);

    try {
        await action();
        await loadUsers(onAuthError);
    } catch (err) {
        if (err.status === 401 || err.status === 403) {
            onAuthError(err.status);
            return;
        }
        // 404 (usuário não encontrado) e 409 (já processado) — nos dois
        // casos o estado real mudou desde a última leitura da lista;
        // recarregar já resolve a UI, além de mostrar o motivo.
        errorEl.textContent = genericErrorMessage(err);
        show(errorEl);
        await loadUsers(onAuthError);
        return;
    }

    button.disabled = false;
    button.textContent = originalLabel;
}

function genericErrorMessage(err) {
    if (err.status === 404) return 'Este usuário não existe mais (talvez já tenha sido removido).';
    if (err.status === 409) return 'Este usuário já foi processado por outra ação — a lista foi atualizada.';
    if (err.status === 0) return 'Não foi possível conectar ao servidor. Verifique sua conexão.';
    if (err.status >= 500) return 'Erro temporário do servidor. Tente novamente em instantes.';
    return err.message || 'Ocorreu um erro inesperado.';
}