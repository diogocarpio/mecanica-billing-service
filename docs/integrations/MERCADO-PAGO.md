# Integração Mercado Pago

## Escopo

O Billing usa o Checkout Pro para criar uma preferência de pagamento para um
orçamento aprovado. O valor e a moeda sempre vêm do orçamento persistido. O
cliente recebe apenas a URL de checkout; tokens, assinatura do webhook e
respostas financeiras completas não são persistidos nem registrados em log.

O estorno não faz parte deste incremento. Se o grupo aprovar essa compensação,
ele deverá ser uma operação auditável no provedor, nunca a exclusão do
pagamento.

## Configuração

As credenciais devem ser fornecidas por variáveis de ambiente:

- `MERCADO_PAGO_ACCESS_TOKEN`: token da aplicação de teste;
- `MERCADO_PAGO_WEBHOOK_SECRET`: chave de assinatura do webhook;
- `MERCADO_PAGO_NOTIFICATION_URL`: URL HTTPS pública do webhook;
- `MERCADO_PAGO_SUCCESS_URL`, `MERCADO_PAGO_FAILURE_URL` e
  `MERCADO_PAGO_PENDING_URL`: redirects do checkout;
- `MERCADO_PAGO_RECONCILIATION_ENABLED=true`: ativa a busca periódica de
  pagamentos pendentes.

Nenhum valor secreto deve entrar no repositório.

## Simulador local de pagamentos

Para demonstração local sem credenciais externas, o repositório inclui
`tools/payment-simulator`, um serviço HTTP Node.js que implementa o mesmo
contrato usado pelo `MercadoPagoHttpGateway`:

- `POST /checkout/preferences` com `Authorization: Bearer` e
  `X-Idempotency-Key`;
- `GET /v1/payments/{id}` e `GET /v1/payments/search?external_reference=...`;
- página de checkout com token próprio e ações **Aprovar**/**Recusar**;
- webhook assinado com HMAC-SHA256 no mesmo manifest esperado pelo Billing;
- retry de webhook e consulta ao "provedor" antes da confirmação local.

O simulador é explicitamente local: usa token/segredo de demonstração, mantém
estado em memória e não processa pagamentos reais. Ele comprova o contrato, a
segurança e a idempotência do Billing, mas não substitui a evidência de um
sandbox real do Mercado Pago quando o grupo optar por executá-lo.

Execução reproduzível:

```bash
docker compose up -d --wait

MERCADO_PAGO_BASE_URL=http://localhost:4010 \
MERCADO_PAGO_ACCESS_TOKEN=local-simulator-access-token \
MERCADO_PAGO_WEBHOOK_SECRET=local-simulator-webhook-secret \
MERCADO_PAGO_NOTIFICATION_URL=http://localhost:8080/api/v1/payments/webhooks/mercado-pago \
./mvnw spring-boot:run
```

Depois de criar/aprovar o orçamento e chamar `POST /api/v1/payments`, abra o
`checkoutUrl` retornado, aprove ou recuse o pagamento e acompanhe o webhook e o
estado no Billing. No Windows, use `mvnw.cmd` e defina as variáveis no terminal
ou em um `.env.local` carregado pelo ambiente de execução.

Configuração do simulador (Compose já define os padrões locais):

- `SIMULATOR_ACCESS_TOKEN`: token Bearer aceito pelo simulador;
- `SIMULATOR_WEBHOOK_SECRET`: segredo HMAC do webhook simulado;
- `SIMULATOR_WEBHOOK_URL`: destino do webhook;
- `SIMULATOR_PUBLIC_URL`: URL pública usada nos links de checkout.

No Compose, `SIMULATOR_WEBHOOK_URL` aponta para o Billing no host
(`host.docker.internal:8080`) e tem precedência sobre o `notification_url` da
preferência, permitindo que o webhook assinado alcance o serviço mesmo quando o
Billing roda fora do container.

Esses valores são credenciais de demonstração, não credenciais Mercado Pago.

## Garantias

- índice único por `quoteId` impede duas cobranças locais;
- a mesma chave de idempotência é reutilizada ao repetir a criação;
- somente orçamento `APPROVED` pode iniciar a cobrança;
- a assinatura usa HMAC-SHA256 e comparação em tempo constante;
- assinaturas fora da tolerância configurada são recusadas;
- após o webhook, o Billing consulta `GET /v1/payments/{id}`;
- `external_reference`, valor e moeda precisam corresponder ao pagamento local;
- notificações duplicadas ou atrasadas não reabrem um estado terminal;
- eventos financeiros são gravados na Outbox na mesma transação do estado.

## Assinatura do webhook

O endpoint é:

`POST /api/v1/payments/webhooks/mercado-pago?data.id={paymentId}&type=payment`

O Mercado Pago envia `x-request-id` e `x-signature`. O manifest validado é:

```text
id:{data.id em minúsculas};request-id:{x-request-id};ts:{ts};
```

O Billing calcula `HMAC-SHA256(webhookSecret, manifest)` e compara o hexadecimal
com o campo `v1` de `x-signature`.

## Teste com Mercado Pago real (opcional/pendente)

1. Criar uma aplicação e usuários de teste no painel Mercado Pago.
2. Configurar as variáveis sem registrar seus valores no terminal ou Git.
3. Publicar o webhook por uma URL HTTPS acessível ao Mercado Pago.
4. Criar e aprovar um orçamento.
5. Criar o pagamento com `POST /api/v1/payments`.
6. Concluir o checkout com um comprador de teste.
7. Confirmar que o webhook gera exatamente um
   `billing.payment.approved.v1`.
8. Guardar apenas evidências sem tokens, assinatura, dados de cartão ou payload
   financeiro sensível.

## Referências oficiais

- <https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-pro/preferences/create-preference/post>
- <https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-pro/get-payment/get>
- <https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/additional-content/notifications/webhooks>
