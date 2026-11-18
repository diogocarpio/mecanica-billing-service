const http = require('node:http');
const crypto = require('node:crypto');
const { URL } = require('node:url');

const DEFAULT_ACCESS_TOKEN = 'local-simulator-access-token';
const DEFAULT_WEBHOOK_SECRET = 'local-simulator-webhook-secret';
const MAX_BODY_BYTES = 1024 * 1024;

function envInt(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function loadConfig(env = process.env) {
    const port = envInt(env.SIMULATOR_PORT, 4010);
    return {
        port,
        publicUrl: (env.SIMULATOR_PUBLIC_URL || `http://localhost:${port}`).replace(/\/$/, ''),
        webhookUrl: env.SIMULATOR_WEBHOOK_URL || 'http://localhost:8080/api/v1/payments/webhooks/mercado-pago',
        webhookUrlOverride: Boolean(env.SIMULATOR_WEBHOOK_URL),
        accessToken: env.SIMULATOR_ACCESS_TOKEN || DEFAULT_ACCESS_TOKEN,
        webhookSecret: env.SIMULATOR_WEBHOOK_SECRET || DEFAULT_WEBHOOK_SECRET,
        webhookTimeoutMs: envInt(env.SIMULATOR_WEBHOOK_TIMEOUT_MS, 5000),
        maxBodyBytes: envInt(env.SIMULATOR_MAX_BODY_BYTES, MAX_BODY_BYTES)
    };
}

function safeEqual(left, right) {
    const leftBuffer = Buffer.from(String(left || ''), 'utf8');
    const rightBuffer = Buffer.from(String(right || ''), 'utf8');
    return leftBuffer.length === rightBuffer.length
        && crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function nowIso() {
    return new Date().toISOString();
}

function sleep(milliseconds) {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function sendJson(response, status, body) {
    const payload = JSON.stringify(body);
    response.writeHead(status, {
        'content-type': 'application/json; charset=utf-8',
        'content-length': Buffer.byteLength(payload)
    });
    response.end(payload);
}

function sendHtml(response, status, html) {
    response.writeHead(status, {
        'content-type': 'text/html; charset=utf-8',
        'content-length': Buffer.byteLength(html)
    });
    response.end(html);
}

function readBody(request, maxBytes) {
    return new Promise((resolve, reject) => {
        const chunks = [];
        let size = 0;
        request.on('data', chunk => {
            size += chunk.length;
            if (size > maxBytes) {
                const error = new Error('Request body too large');
                error.status = 413;
                request.destroy();
                reject(error);
                return;
            }
            chunks.push(chunk);
        });
        request.on('end', () => resolve(Buffer.concat(chunks)));
        request.on('error', reject);
    });
}

async function parseJsonBody(request, maxBytes) {
    const raw = await readBody(request, maxBytes);
    if (raw.length === 0) {
        return {};
    }
    try {
        return JSON.parse(raw.toString('utf8'));
    } catch {
        const error = new Error('Invalid JSON body');
        error.status = 400;
        throw error;
    }
}

function parseFormBody(raw) {
    return Object.fromEntries(new URLSearchParams(raw.toString('utf8')));
}

function bearerToken(request) {
    const header = request.headers.authorization || '';
    const [scheme, token] = header.split(/\s+/, 2);
    return scheme === 'Bearer' ? token : null;
}

function isAuthorized(request, config) {
    return safeEqual(bearerToken(request), config.accessToken);
}

function amountFromPreference(body) {
    const items = Array.isArray(body.items) ? body.items : [];
    const amount = items.reduce((total, item) => {
        const quantity = Number(item.quantity ?? 1);
        const unitPrice = Number(item.unit_price);
        if (!Number.isFinite(quantity) || quantity <= 0
            || !Number.isFinite(unitPrice) || unitPrice < 0) {
            return Number.NaN;
        }
        return total + quantity * unitPrice;
    }, 0);
    return Number.isFinite(amount) ? Math.round(amount * 100) / 100 : null;
}

function currencyFromPreference(body) {
    const items = Array.isArray(body.items) ? body.items : [];
    const currency = items.find(item => item.currency_id)?.currency_id
        || body.currency_id
        || 'BRL';
    return String(currency).toUpperCase();
}

function providerPayment(payment) {
    return {
        id: payment.id,
        external_reference: payment.externalReference,
        transaction_amount: payment.amount,
        currency_id: payment.currency,
        status: payment.status,
        status_detail: payment.statusDetail,
        date_created: payment.createdAt,
        date_last_updated: payment.updatedAt,
        payer: payment.payerEmail ? { email: payment.payerEmail } : undefined,
        notification_url: payment.notificationUrl
    };
}

function preferenceResponse(payment) {
    return {
        id: payment.preferenceId,
        init_point: payment.checkoutUrl,
        sandbox_init_point: payment.checkoutUrl
    };
}

function webhookSignature(payment, config) {
    const requestId = `sim-req-${crypto.randomUUID()}`;
    const timestamp = Date.now();
    const manifest = `id:${payment.id.toLowerCase()};request-id:${requestId};ts:${timestamp};`;
    const hash = crypto.createHmac('sha256', config.webhookSecret)
        .update(manifest, 'utf8')
        .digest('hex');
    return {
        requestId,
        signature: `ts=${timestamp},v1=${hash}`
    };
}

function webhookUrlFor(payment, config) {
    const raw = config.webhookUrlOverride ? config.webhookUrl : (payment.notificationUrl || config.webhookUrl);
    if (!raw) {
        return null;
    }
    let url;
    try {
        url = new URL(raw);
    } catch {
        return null;
    }
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return null;
    }
    url.searchParams.set('data.id', payment.id);
    url.searchParams.set('type', 'payment');
    return url;
}

async function deliverWebhook(payment, config, deliveries, reason) {
    const url = webhookUrlFor(payment, config);
    if (!url) {
        const delivery = {
            at: nowIso(),
            reason,
            ok: false,
            attempts: 0,
            error: 'webhook_url_not_configured'
        };
        payment.webhookHistory.push(delivery);
        deliveries.push(delivery);
        return delivery;
    }

    let lastDelivery = null;
    for (let attempt = 1; attempt <= 3; attempt++) {
        const signed = webhookSignature(payment, config);
        const startedAt = nowIso();
        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'content-type': 'application/json',
                    'x-request-id': signed.requestId,
                    'x-signature': signed.signature,
                    'user-agent': 'mecanica-payment-simulator/1.0'
                },
                body: JSON.stringify({
                    action: 'payment.updated',
                    api_version: 'v1',
                    data: { id: payment.id },
                    date_created: startedAt,
                    id: Number.parseInt(payment.id.replace(/\D/g, ''), 10) || payment.id,
                    live_mode: false,
                    simulator: true,
                    type: 'payment',
                    user_id: 'simulator-user'
                }),
                signal: AbortSignal.timeout(config.webhookTimeoutMs)
            });
            const delivery = {
                at: nowIso(),
                reason,
                attempt,
                ok: response.ok,
                status: response.status,
                requestId: signed.requestId
            };
            if (!response.ok) {
                delivery.error = `webhook_http_${response.status}`;
            }
            lastDelivery = delivery;
            payment.webhookHistory.push(delivery);
            deliveries.push(delivery);
            if (response.ok) {
                return delivery;
            }
        } catch (error) {
            lastDelivery = {
                at: nowIso(),
                reason,
                attempt,
                ok: false,
                error: error.name === 'TimeoutError' ? 'webhook_timeout' : 'webhook_request_failed'
            };
            payment.webhookHistory.push(lastDelivery);
            deliveries.push(lastDelivery);
        }
        if (attempt < 3) {
            await sleep(250 * attempt);
        }
    }
    return lastDelivery;
}

function targetStatus(action) {
    if (action === 'approve') {
        return 'approved';
    }
    if (action === 'reject') {
        return 'rejected';
    }
    return null;
}

function statusDetailFor(status) {
    return status === 'approved' ? 'accredited' : 'rejected_by_user';
}

function renderCheckoutPage(payment, options = {}) {
    const message = options.message ? `<p class="message">${escapeHtml(options.message)}</p>` : '';
    const delivery = options.delivery ? `
        <p class="message ${options.delivery.ok ? 'ok' : 'error'}">
            Webhook ${options.delivery.ok ? 'entregue' : 'não entregue'}
            ${options.delivery.status ? ` (HTTP ${options.delivery.status})` : ''}
            em ${options.delivery.attempt || 1} tentativa(s).
        </p>` : '';
    const actions = options.showActions === false ? '' : payment.status === 'pending' || payment.status === 'in_process'
        ? `
            <form method="post" action="/checkout/${encodeURIComponent(payment.id)}/decision">
                <input type="hidden" name="token" value="${escapeHtml(payment.checkoutToken)}">
                <input type="hidden" name="action" value="approve">
                <button class="approve" type="submit">Aprovar pagamento</button>
            </form>
            <form method="post" action="/checkout/${encodeURIComponent(payment.id)}/decision">
                <input type="hidden" name="token" value="${escapeHtml(payment.checkoutToken)}">
                <input type="hidden" name="action" value="reject">
                <button class="reject" type="submit">Recusar pagamento</button>
            </form>`
        : `
            <form method="post" action="/checkout/${encodeURIComponent(payment.id)}/webhook">
                <input type="hidden" name="token" value="${escapeHtml(payment.checkoutToken)}">
                <button class="secondary" type="submit">Reenviar notificação</button>
            </form>`;

    return `<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Checkout simulado — Billing</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 0; background: #f4f6f8; color: #17202a; }
        main { max-width: 560px; margin: 48px auto; background: white; border-radius: 12px; padding: 28px; box-shadow: 0 8px 24px rgba(0,0,0,.08); }
        h1 { font-size: 22px; margin-top: 0; }
        dl { display: grid; grid-template-columns: 140px 1fr; gap: 8px 16px; }
        dt { color: #5d6d7e; } dd { margin: 0; word-break: break-word; }
        form { display: inline-block; margin: 12px 8px 0 0; }
        button { border: 0; border-radius: 8px; padding: 12px 18px; font-weight: 700; cursor: pointer; }
        .approve { background: #1e8449; color: white; }
        .reject { background: #b03a2e; color: white; }
        .secondary { background: #d5dbdb; color: #17202a; }
        .message { padding: 10px 12px; border-radius: 8px; background: #eafaf1; }
        .message.error { background: #fdecea; }
        .badge { display: inline-block; padding: 4px 10px; border-radius: 999px; background: #eaf2f8; font-weight: 700; }
    </style>
</head>
<body>
<main>
    <h1>Checkout simulado</h1>
    <p>Este provedor é local e não processa pagamentos reais.</p>
    ${message}
    ${delivery}
    <dl>
        <dt>Pagamento</dt><dd><span class="badge">${escapeHtml(payment.status)}</span> ${escapeHtml(payment.id)}</dd>
        <dt>Referência</dt><dd>${escapeHtml(payment.externalReference)}</dd>
        <dt>Valor</dt><dd>${escapeHtml(payment.currency)} ${Number(payment.amount).toFixed(2)}</dd>
        <dt>Detalhe</dt><dd>${escapeHtml(payment.statusDetail || '-')}</dd>
        <dt>Preferência</dt><dd>${escapeHtml(payment.preferenceId)}</dd>
        <dt>Criado em</dt><dd>${escapeHtml(payment.createdAt)}</dd>
        <dt>Atualizado em</dt><dd>${escapeHtml(payment.updatedAt)}</dd>
    </dl>
    ${actions}
</main>
</body>
</html>`;
}

function checkoutTokenValid(payment, token) {
    return payment && safeEqual(payment.checkoutToken, token);
}

function createServer(config = loadConfig()) {
    const payments = new Map();
    const paymentsByExternalReference = new Map();
    const preferencesByIdempotencyKey = new Map();
    const deliveries = [];
    let sequence = 0;

    function createPayment(body) {
        sequence += 1;
        const suffix = crypto.randomBytes(4).toString('hex');
        const id = `sim_pay_${String(sequence).padStart(6, '0')}_${suffix}`;
        const preferenceId = `pref_sim_${String(sequence).padStart(6, '0')}_${suffix}`;
        const checkoutToken = crypto.randomBytes(24).toString('base64url');
        const timestamp = nowIso();
        const payment = {
            id,
            preferenceId,
            checkoutToken,
            checkoutUrl: `${config.publicUrl}/checkout/${id}?token=${checkoutToken}`,
            externalReference: body.external_reference,
            amount: amountFromPreference(body),
            currency: currencyFromPreference(body),
            payerEmail: body.payer?.email,
            notificationUrl: body.notification_url || config.webhookUrl,
            status: 'pending',
            statusDetail: 'pending',
            createdAt: timestamp,
            updatedAt: timestamp,
            webhookHistory: []
        };
        payments.set(id, payment);
        paymentsByExternalReference.set(payment.externalReference, payment);
        return payment;
    }

    async function createPreference(request, response) {
        if (!isAuthorized(request, config)) {
            return sendJson(response, 401, { error: 'unauthorized', message: 'Invalid simulator access token' });
        }
        const body = await parseJsonBody(request, config.maxBodyBytes);
        const externalReference = typeof body.external_reference === 'string'
            ? body.external_reference.trim()
            : '';
        const idempotencyKey = String(request.headers['x-idempotency-key'] || '').trim();
        if (!externalReference) {
            return sendJson(response, 400, { error: 'invalid_request', message: 'external_reference is required' });
        }
        if (!idempotencyKey) {
            return sendJson(response, 400, { error: 'invalid_request', message: 'X-Idempotency-Key is required' });
        }

        const reused = preferencesByIdempotencyKey.get(idempotencyKey);
        if (reused && reused.externalReference !== externalReference) {
            return sendJson(response, 409, { error: 'idempotency_conflict', message: 'Idempotency key was used for another payment' });
        }

        let payment = paymentsByExternalReference.get(externalReference);
        if (!payment) {
            const amount = amountFromPreference(body);
            if (amount === null || amount <= 0) {
                return sendJson(response, 400, { error: 'invalid_request', message: 'items must contain a positive amount' });
            }
            if (!/^[A-Z]{3}$/.test(currencyFromPreference(body))) {
                return sendJson(response, 400, { error: 'invalid_request', message: 'currency_id must have three letters' });
            }
            payment = createPayment(body);
        }
        preferencesByIdempotencyKey.set(idempotencyKey, {
            paymentId: payment.id,
            externalReference: payment.externalReference
        });
        return sendJson(response, 200, preferenceResponse(payment));
    }

    function findProviderPayment(request, response, pathname) {
        if (!isAuthorized(request, config)) {
            return sendJson(response, 401, { error: 'unauthorized', message: 'Invalid simulator access token' });
        }
        if (pathname === '/v1/payments/search') {
            const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
            const externalReference = url.searchParams.get('external_reference');
            const results = externalReference && paymentsByExternalReference.has(externalReference)
                ? [providerPayment(paymentsByExternalReference.get(externalReference))]
                : [];
            return sendJson(response, 200, { results, paging: { total: results.length, limit: 30, offset: 0 } });
        }
        const id = decodeURIComponent(pathname.split('/').pop());
        const payment = payments.get(id);
        if (!payment) {
            return sendJson(response, 404, { error: 'not_found', message: 'Payment not found' });
        }
        return sendJson(response, 200, providerPayment(payment));
    }

    async function decidePayment(request, response, paymentId, action, resendOnly = false) {
        const payment = payments.get(paymentId);
        const raw = await readBody(request, config.maxBodyBytes);
        const form = parseFormBody(raw);
        if (!checkoutTokenValid(payment, form.token)) {
            return sendHtml(response, 403, renderCheckoutPage(payment || { id: paymentId, status: 'unknown' }, { message: 'Link de checkout inválido ou expirado.', showActions: false }));
        }
        if (resendOnly) {
            const delivery = await deliverWebhook(payment, config, deliveries, 'manual-resend');
            return sendHtml(response, 200, renderCheckoutPage(payment, {
                message: delivery.ok ? 'Notificação reenviada com sucesso.' : 'Falha ao reenviar a notificação.',
                delivery
            }));
        }

        const target = targetStatus(action || form.action);
        if (!target) {
            return sendHtml(response, 400, renderCheckoutPage(payment, { message: 'Ação inválida.' }));
        }
        if (payment.status === 'pending' || payment.status === 'in_process') {
            payment.status = target;
            payment.statusDetail = statusDetailFor(target);
            payment.updatedAt = nowIso();
            const delivery = await deliverWebhook(payment, config, deliveries, 'checkout-decision');
            return sendHtml(response, 200, renderCheckoutPage(payment, {
                message: target === 'approved' ? 'Pagamento aprovado.' : 'Pagamento recusado.',
                delivery
            }));
        }
        if (payment.status === target) {
            return sendHtml(response, 200, renderCheckoutPage(payment, { message: 'Este pagamento já possui o status informado.' }));
        }
        return sendHtml(response, 409, renderCheckoutPage(payment, { message: 'Pagamento já está em estado final.' }));
    }

    async function handleCheckout(request, response, pathname) {
        const match = pathname.match(/^\/checkout\/([^/]+)(?:\/(decision|webhook))?$/);
        if (!match) {
            return sendJson(response, 404, { error: 'not_found' });
        }
        const paymentId = decodeURIComponent(match[1]);
        const action = match[2];
        const payment = payments.get(paymentId);
        if (!payment) {
            return sendHtml(response, 404, renderCheckoutPage({ id: paymentId, status: 'not_found', externalReference: '-', amount: 0, currency: 'BRL', preferenceId: '-', createdAt: '-', updatedAt: '-', webhookHistory: [] }, { message: 'Pagamento não encontrado.', showActions: false }));
        }

        if (request.method === 'GET' && !action) {
            const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
            if (!checkoutTokenValid(payment, url.searchParams.get('token'))) {
                return sendHtml(response, 403, renderCheckoutPage(payment, { message: 'Link de checkout inválido ou expirado.', showActions: false }));
            }
            return sendHtml(response, 200, renderCheckoutPage(payment));
        }
        if (request.method === 'POST' && action === 'decision') {
            return decidePayment(request, response, paymentId, null, false);
        }
        if (request.method === 'POST' && action === 'webhook') {
            return decidePayment(request, response, paymentId, null, true);
        }
        return sendJson(response, 405, { error: 'method_not_allowed' });
    }

    const server = http.createServer(async (request, response) => {
        const startedAt = Date.now();
        const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
        const pathname = url.pathname;
        try {
            let handled = true;
            if (request.method === 'GET' && pathname === '/health') {
                sendJson(response, 200, { status: 'ok', payments: payments.size });
            } else if (request.method === 'GET' && pathname === '/') {
                sendJson(response, 200, {
                    service: 'mecanica-payment-simulator',
                    mode: 'local-demo',
                    endpoints: {
                        preferences: 'POST /checkout/preferences',
                        payment: 'GET /v1/payments/{id}',
                        search: 'GET /v1/payments/search?external_reference={id}',
                        checkout: 'GET /checkout/{paymentId}?token={checkoutToken}'
                    }
                });
            } else if (request.method === 'POST' && pathname === '/checkout/preferences') {
                await createPreference(request, response);
            } else if (request.method === 'GET' && pathname.startsWith('/v1/payments')) {
                findProviderPayment(request, response, pathname);
            } else if (pathname.startsWith('/checkout/')) {
                await handleCheckout(request, response, pathname);
            } else {
                handled = false;
                sendJson(response, 404, { error: 'not_found' });
            }
            console.log(`${new Date().toISOString()} ${request.method} ${pathname} ${response.statusCode} ${Date.now() - startedAt}ms`);
        } catch (error) {
            const status = error.status || 500;
            if (!response.headersSent) {
                if (pathname.startsWith('/checkout/')) {
                    sendHtml(response, status, renderCheckoutPage({ id: 'unknown', status: 'error', externalReference: '-', amount: 0, currency: 'BRL', preferenceId: '-', createdAt: '-', updatedAt: '-', webhookHistory: [] }, { message: error.message || 'Erro interno do simulador.', showActions: false }));
                } else {
                    sendJson(response, status, { error: 'simulator_error', message: error.message || 'Internal simulator error' });
                }
            }
            console.error(`${new Date().toISOString()} ERROR ${request.method} ${pathname}: ${error.stack || error}`);
        }
    });

    server.paymentSimulator = {
        config,
        payments,
        deliveries
    };
    return server;
}

if (require.main === module) {
    const config = loadConfig();
    const server = createServer(config);
    server.listen(config.port, '0.0.0.0', () => {
        console.log(`Payment simulator listening on ${config.publicUrl}`);
        console.log(`Webhook target: ${config.webhookUrl}`);
        console.log('Local demo credentials are enabled; do not expose this service outside local development.');
    });
}

module.exports = { createServer, loadConfig };
