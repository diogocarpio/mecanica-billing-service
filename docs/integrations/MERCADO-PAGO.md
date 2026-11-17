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

## Teste sandbox pendente

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
