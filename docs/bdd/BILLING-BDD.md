# BDD do Billing

O cenário executável está em
`src/test/resources/features/billing-payment.feature`. Ele comprova que:

- um orçamento aprovado habilita a criação do checkout;
- repetições técnicas não criam outro pagamento;
- o webhook autenticado é confirmado consultando o provedor;
- o pagamento termina como `APPROVED`.

O cenário de falha/compensação local está em
`src/test/resources/features/billing-saga-failure.feature`. Ele comprova que:

- uma solicitação de orçamento inválida (`items` vazio) processada pelo
  `QuoteSagaHandler` publica `billing.quote.failed.v1` com código
  `INVALID_QUOTE_DATA` via Outbox;
- o cancelamento da OS (`os.cancelled.v1`) compensa o orçamento pendente,
  que termina como `CANCELLED` e publica `billing.quote.cancelled.v1`.

Os limites do OS Service e do Mercado Pago são simulados no teste do
repositório Billing. Para o BDD final do grupo, o mesmo cenário deve:

1. iniciar por uma OS real no OS Service;
2. receber os eventos de orçamento pelo RabbitMQ;
3. aprovar o orçamento pela API do Billing;
4. usar o sandbox real ou o stub HTTP controlado do Mercado Pago;
5. confirmar que o OS Service consumiu `billing.payment.approved.v1`;
6. verificar o estado final da OS sem acessar bancos de outros serviços.

O cenário cross-service permanece uma atividade conjunta porque depende dos
repositórios, contratos implantados e ambiente dos demais alunos. O BDD de
falha/compensação acima é local (mocks de repositório, Inbox e Outbox); a
versão ponta a ponta com broker real e os demais serviços continua pendente.

## Cenários cross-service a executar com o grupo

Ambiente mínimo: OS Service, Billing Service, RabbitMQ com exchange/DLX,
MongoDB do Billing e Mercado Pago sandbox ou stub HTTP acordado.

### Fluxo feliz

1. OS Service publica `os.quote.requested.v1` para uma OS real.
2. Billing consome de forma idempotente, persiste o orçamento e publica
   `billing.quote.created.v1`/`billing.quote.submitted.v1`.
3. Cliente aprova pela API do Billing; Billing publica
   `billing.quote.approved.v1`.
4. `POST /api/v1/payments` cria uma única preferência e retorna checkout.
5. Webhook assinado é validado, o provedor é consultado e o pagamento vai a
   `APPROVED`; Billing publica `billing.payment.approved.v1`.
6. OS Service consome os eventos e atualiza a OS sem acessar o banco do
   Billing.

### Falha/compensação

1. OS Service publica `os.cancelled.v1` para uma OS com orçamento pendente.
2. Billing marca o orçamento como `CANCELLED` e publica
   `billing.quote.cancelled.v1` sem efeitos duplicados.
3. Uma solicitação inválida deve publicar `billing.quote.failed.v1` e impedir
   avanço da OS.
4. Uma falha transitória de publicação deve aparecer em retry/DLQ e ser
   reprocessada sem duplicar Inbox/Outbox.

Esses cenários só podem ser marcados como validados depois de executados
contra os serviços reais; os testes locais do Billing não substituem essa
evidência.
