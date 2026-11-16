# Reprocessamento seguro de DLQ e Outbox

Runbook operacional para inspeção e reprocessamento de mensagens que
esgotaram os retries do Billing Service. Use-o sempre que a métrica
`billing.outbox.failed` subir ou mensagens aparecerem nas filas de DLQ.

## 1. DLQs do RabbitMQ

As filas de destino das mensagens com falha definitiva seguem o padrão
`<queue>.dlq` no virtual host padrão, encaminhadas pelo exchange de dead
letter `mecanica.events.dlx` (configuração em
`src/main/resources/application.yml` e topologia em
[docs/architecture/SAGA-BILLING.md](architecture/SAGA-BILLING.md)).

Para inspecionar uma mensagem, identifique:

- `eventId`, `correlationId`, `osId` e `eventType` no corpo JSON;
- headers AMQP (`correlationId`, `eventId`) propagados pela Outbox;
- `x-death` (razão, fila de origem, contagem de reentregas) e timestamp de
  morte;
- o payload do evento e sua versão (`eventVersion`).

## 2. Regras de ouro

- **Nunca apague uma mensagem antes de registrar evidência**: anote data,
  `eventId`, `osId`, `eventType`, causa raiz, responsável e decisão no
  registro de incidentes do grupo.
- **Não reprocessar payload inválido sem corrigir o contrato/versão**:
  mensagem que falha por schema ou versão incompatível voltará à DLQ; a
  correção deve acontecer no produtor ou via nova versão do evento.
- O reprocessamento é manual e controlado — não automatize requeue em
  massa sem revisão.

## 3. Reprocessamento seguro de mensagem DLQ

1. Confirme a causa raiz (contrato, dependência indisponível, bug, dado
   inválido).
2. Se a falha era transitória e já resolvida, republicque **o mesmo
   evento** para a routing key original no exchange `mecanica.events`,
   preservando `eventId`, `correlationId` e `causationId`.
3. A Inbox deduplica por `eventId`: se o efeito já tiver sido aplicado,
   o reprocessamento é ignorado com segurança.
4. Valide o consumo pela métrica `billing.messaging.consumed` e pelos
   logs com `correlationId`/`eventId`.
5. Remova a mensagem da DLQ somente após confirmar o processamento (ou
   registre a decisão de descarte como compensação de negócio).

## 4. Eventos `FAILED` na Outbox (`outbox_events`)

Quando a publicação falha definitivamente, o registro fica com
`status=FAILED` na coleção `outbox_events` do MongoDB.

1. Inspecione os registros:

   ```js
   db.outbox_events.find({ status: "FAILED" }).sort({ createdAt: 1 })
   ```

2. Corrija a causa raiz (broker indisponível, payload não serializável,
   erro de rede).
3. Somente então reabra os registros manualmente para retry, zerando
   tentativas e limpando o erro — operação controlada, nunca em massa
   sem revisão. O `eventId` do evento é persistido como `_id` do documento:

   ```js
   db.outbox_events.updateMany(
     { status: "FAILED", _id: "<eventId>" },
     { $set: { status: "PENDING", attempts: 0, nextAttemptAt: new Date(), lastError: null } }
   )
   ```

4. Acompanhe `billing.outbox.publish` (sucessos) e `billing.outbox.failed`
   (falhas definitivas) até o evento ser publicado.

## 5. Métricas e registro

Monitore em `/actuator/prometheus`:

- `billing.messaging.consumed` — eventos consumidos por tipo;
- `billing.outbox.publish` — eventos publicados pela Outbox;
- `billing.outbox.failed` — falhas definitivas de publicação.

Para cada incidente, registre: data/hora, `eventId`, `osId`, `eventType`,
causa raiz, ação tomada e responsável.
