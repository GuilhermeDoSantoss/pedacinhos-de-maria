import { CONFIG } from './config.js';
import { authHeader } from './session.js';

export class AdminApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || 'Erro ao comunicar com o servidor');
        this.status = status;
    }
}

/** status: 'PENDING' | 'APPROVED' | 'REJECTED' | null (null = todos). */
export async function fetchUsers(status) {
    const url = new URL(`${CONFIG.API_BASE_URL}/admin/users`);
    if (status) url.searchParams.set('status', status);

    const response = await fetch(url, { headers: { ...authHeader() } });
    return handleResponse(response);
}

export async function approveUser(id) {
    const response = await fetch(`${CONFIG.API_BASE_URL}/admin/users/${encodeURIComponent(id)}/approve`, {
        method: 'PATCH',
        headers: { ...authHeader() },
    });
    return handleResponse(response);
}

export async function rejectUser(id) {
    const response = await fetch(`${CONFIG.API_BASE_URL}/admin/users/${encodeURIComponent(id)}/reject`, {
        method: 'PATCH',
        headers: { ...authHeader() },
    });
    return handleResponse(response);
}

async function handleResponse(response) {
    const body = await safeJson(response);
    if (!response.ok) {
        throw new AdminApiError(response.status, body);
    }
    return body;
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}