/**
 * Cliente STOMP mínimo, implementado à mão sobre a API WebSocket nativa do
 * navegador. Cobre só o subconjunto do protocolo que este projeto usa:
 * CONNECT, CONNECTED, SUBSCRIBE, MESSAGE, ERROR.
 *
 * Por que não usar stomp.js: o requisito do projeto é frontend 100% vanilla
 * JS, sem bibliotecas de terceiros. STOMP é um protocolo de texto simples o
 * bastante (frames delimitados por linha em branco + terminados em \0) para
 * ser implementado em poucas dezenas de linhas — não precisa da biblioteca
 * completa para os 3 tipos de frame que consumimos.
 *
 * O backend continua usando Spring + STOMP de verdade (com broker relay como
 * caminho de upgrade futuro) — só o cliente é artesanal, não o protocolo.
 *
 * Esta mesma classe é usada pelo Customer App, sem nenhuma mudança — o
 * parâmetro `connectHeaders` é opcional (default {}) e só o Kitchen
 * Dashboard o utiliza, para enviar Authorization no CONNECT.
 *
 * Formato de um frame STOMP:
 *   COMMAND
 *   header1:value1
 *   header2:value2
 *
 *   corpo (opcional)
 *   \0
 */

const RECONNECT_DELAYS_MS = [1000, 2000, 4000, 8000, 16000, 30000];

export class StompClient {
    #url;
    #socket = null;
    #connected = false;
    #subscriptions = new Map(); // id -> { destination, callback }
    #nextSubscriptionId = 0;
    #reconnectAttempt = 0;
    #reconnectTimer = null;
    #manuallyClosed = false;
    #connectHeaders;

    constructor(url) {
        this.#url = url;
    }

    /**
     * Abre a conexão e envia o CONNECT. `onReady` é chamado toda vez que uma
     * conexão STOMP é estabelecida — inclusive após reconexões automáticas —
     * porque é responsabilidade do chamador reassinar os tópicos necessários
     * a cada `onReady` (ver connectAndSubscribe mais abaixo, que já cobre isso).
     *
     * `connectHeaders` (opcional) é chamado a cada tentativa de conexão —
     * não só a primeira — para que um `Authorization` baseado em token
     * sempre reflita a sessão atual, mesmo depois de uma reconexão automática.
     */
    connect(onReady, onError, connectHeaders = {}) {
        this.#manuallyClosed = false;
        this.#connectHeaders = connectHeaders;
        this.#openSocket(onReady, onError);
    }

    #openSocket(onReady, onError) {
        this.#socket = new WebSocket(this.#url);

        this.#socket.onopen = () => {
            // Header STOMP 'host' é metadado de virtual-hosting do protocolo,
            // não afeta a URL de conexão real (essa já vem por parâmetro no
            // construtor). O broker simples do Spring (ver WebSocketConfig)
            // não valida esse valor, mas usar o hostname real da página em
            // vez de 'localhost' fixo evita qualquer confusão futura ao
            // inspecionar frames STOMP no DevTools em produção.
            const headers = typeof this.#connectHeaders === 'function'
                ? this.#connectHeaders()
                : this.#connectHeaders;

            this.#sendFrame('CONNECT', {
                'accept-version': '1.2',
                host: window.location.hostname,
                ...headers,
            });
        };

        this.#socket.onmessage = (event) => this.#handleFrame(event.data, onReady);

        this.#socket.onerror = (event) => {
            if (onError) onError(event);
        };

        this.#socket.onclose = () => {
            this.#connected = false;
            if (!this.#manuallyClosed) {
                this.#scheduleReconnect(onReady, onError);
            }
        };
    }

    #scheduleReconnect(onReady, onError) {
        const delay = RECONNECT_DELAYS_MS[Math.min(this.#reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];
        this.#reconnectAttempt += 1;

        this.#reconnectTimer = setTimeout(() => {
            this.#openSocket(onReady, onError);
        }, delay);
    }

    #handleFrame(rawData, onReady) {
        const { command, headers, body } = parseStompFrame(rawData);

        if (command === 'CONNECTED') {
            this.#connected = true;
            this.#reconnectAttempt = 0;
            this.#resubscribeAll();
            if (onReady) onReady();
            return;
        }

        if (command === 'MESSAGE') {
            const destination = headers.destination;
            for (const sub of this.#subscriptions.values()) {
                if (sub.destination === destination) {
                    let payload = body;
                    try {
                        payload = JSON.parse(body);
                    } catch {
                        // corpo não era JSON — entrega como texto puro, chamador decide o que fazer
                    }
                    sub.callback(payload);
                }
            }
            return;
        }

        if (command === 'ERROR') {
            console.error('STOMP ERROR frame recebido:', headers, body);
        }
    }

    /** Reenvia SUBSCRIBE para todos os tópicos ativos — necessário após cada reconexão. */
    #resubscribeAll() {
        for (const [id, sub] of this.#subscriptions.entries()) {
            this.#sendFrame('SUBSCRIBE', { id, destination: sub.destination });
        }
    }

    subscribe(destination, callback) {
        const id = `sub-${this.#nextSubscriptionId++}`;
        this.#subscriptions.set(id, { destination, callback });

        if (this.#connected) {
            this.#sendFrame('SUBSCRIBE', { id, destination });
        }
        // Se ainda não conectou, a assinatura é enviada automaticamente em
        // #resubscribeAll assim que o CONNECTED chegar.

        return id;
    }

    unsubscribe(id) {
        if (!this.#subscriptions.has(id)) return;
        this.#subscriptions.delete(id);
        if (this.#connected) {
            this.#sendFrame('UNSUBSCRIBE', { id });
        }
    }

    disconnect() {
        this.#manuallyClosed = true;
        clearTimeout(this.#reconnectTimer);
        if (this.#socket && this.#connected) {
            this.#sendFrame('DISCONNECT', {});
        }
        this.#socket?.close();
    }

    #sendFrame(command, headers, body = '') {
        if (!this.#socket || this.#socket.readyState !== WebSocket.OPEN) return;

        const headerLines = Object.entries(headers)
            .map(([key, value]) => `${key}:${value}`)
            .join('\n');

        const frame = `${command}\n${headerLines}\n\n${body}\0`;
        this.#socket.send(frame);
    }
}

function parseStompFrame(rawData) {
    const withoutTerminator = rawData.endsWith('\0') ? rawData.slice(0, -1) : rawData;
    const [headerBlock, ...bodyParts] = withoutTerminator.split('\n\n');
    const body = bodyParts.join('\n\n');

    const lines = headerBlock.split('\n');
    const command = lines[0];
    const headers = {};

    for (let i = 1; i < lines.length; i++) {
        const separatorIndex = lines[i].indexOf(':');
        if (separatorIndex === -1) continue;
        const key = lines[i].slice(0, separatorIndex);
        const value = lines[i].slice(separatorIndex + 1);
        headers[key] = value;
    }

    return { command, headers, body };
}