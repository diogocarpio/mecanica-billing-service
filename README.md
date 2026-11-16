# Mecanica Billing Service

Repositório: <https://github.com/alexandrasoares/mecanica-billing-service>

Microsserviço responsável por orçamentos e pagamentos das ordens de serviço da
oficina mecânica.

## Arquitetura

O Billing é o dono do domínio de orçamento e pagamento: cada OS possui no
máximo um orçamento, com itens calculados no próprio serviço, estados
versionados e persistência em MongoDB próprio (replica set). A comunicação
com os demais serviços é assíncrona via RabbitMQ (topic exchange `mecanica.events`
com DLX), usando padrões Inbox/Outbox para idempotência e publicação confiável.
O pagamento externo usa a API do Mercado Pago: um link de checkout é gerado
somente após a aprovação do orçamento e o webhook assinado confirma o resultado.

A integração ocorre por Saga coreografada baseada em eventos; o Billing não
acessa bancos de outros serviços e nunca atualiza o status da OS diretamente.

- [Decisão arquitetural da Saga](docs/architecture/ADR-001-saga-coreografada.md)
- [Fluxo, topologia e compensações](docs/architecture/SAGA-BILLING.md)
- [Contrato AsyncAPI](docs/contracts/asyncapi.yaml)
- [Integração Mercado Pago](docs/integrations/MERCADO-PAGO.md)

## Estado atual

O Billing já permite:

- criar e consultar um orçamento;
- calcular o total a partir de peças e mão de obra;
- enviar o orçamento para aprovação;
- aprovar ou rejeitar o orçamento;
- persistir no MongoDB;
- validar estados, valores e duplicidade por OS;
- consumir solicitações e cancelamentos pelo RabbitMQ de forma idempotente;
- publicar eventos por Outbox com confirmação do broker;
- aplicar retry e encaminhar falhas definitivas para DLQ;
- criar uma preferência idempotente no Mercado Pago para orçamento aprovado;
- validar a assinatura do webhook e consultar o pagamento no provedor;
- confirmar pagamentos sem aceitar valor ou moeda divergentes;
- reconciliar pagamentos pendentes e publicar seus resultados pela Outbox;
- consultar o contrato OpenAPI e métricas operacionais.

O teste controlado com credenciais reais de sandbox ainda depende da aplicação
Mercado Pago da equipe.

## Stack

- Java 17;
- Spring Boot 3.5;
- MongoDB;
- RabbitMQ;
- Maven e JaCoCo;
- Springdoc OpenAPI;
- Actuator e Prometheus;
- Docker Compose.

## Executar localmente

Pré-requisitos:

- Java 17 configurado em `JAVA_HOME`;
- Docker Desktop ou Docker Engine com Compose.

Suba as dependências:

```bash
docker compose up -d --wait
```

Compile, execute os testes e valide a cobertura:

```bash
./mvnw clean verify
```

Inicie a aplicação:

```bash
./mvnw spring-boot:run
```

No Windows também é possível utilizar `mvnw.cmd`.

## Endpoints

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/v1/quotes` | Criar orçamento em rascunho |
| `GET` | `/api/v1/quotes/{id}` | Consultar por ID |
| `GET` | `/api/v1/quotes?osId={osId}` | Consultar por OS |
| `POST` | `/api/v1/quotes/{id}/submit` | Enviar para aprovação |
| `POST` | `/api/v1/quotes/{id}/approve` | Aprovar |
| `POST` | `/api/v1/quotes/{id}/reject` | Rejeitar |
| `POST` | `/api/v1/payments` | Criar ou recuperar checkout idempotente |
| `GET` | `/api/v1/payments/{id}` | Consultar pagamento por ID |
| `GET` | `/api/v1/payments?quoteId={quoteId}` | Consultar por orçamento |
| `GET` | `/api/v1/payments?osId={osId}` | Consultar por OS |
| `POST` | `/api/v1/payments/webhooks/mercado-pago` | Receber webhook assinado |

Exemplo de criação:

```bash
curl -X POST http://localhost:8080/api/v1/quotes \
  -H "Content-Type: application/json" \
  -d '{
    "osId": "OS-001",
    "customerId": "CUSTOMER-001",
    "items": [
      {
        "description": "Troca de óleo",
        "type": "LABOR",
        "quantity": 1,
        "unitPrice": 120.00
      },
      {
        "description": "Filtro de óleo",
        "type": "PART",
        "quantity": 1,
        "unitPrice": 45.50
      }
    ],
    "currency": "BRL",
    "validUntil": "2026-12-01T12:00:00Z"
  }'
```

## Container e Kubernetes

Construir e executar a imagem:

```bash
docker build -t mecanica/billing-service:local .
docker run -p 8080:8080 \
  -e MONGODB_URI='mongodb://...' \
  -e RABBITMQ_HOST='...' \
  mecanica/billing-service:local
```

Manifests em `k8s/` (`namespace`, `configmap`, `secret`, `deployment`,
`service`). O `secret.yaml` contém apenas placeholders — crie o Secret real no
cluster com `kubectl create secret`, conforme as instruções no próprio arquivo.
O container roda como usuário não root e expõe probes em
`/actuator/health/liveness` e `/actuator/health/readiness`.

## URLs locais

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/api-docs>
- Health: <http://localhost:8080/actuator/health>
- Prometheus: <http://localhost:8080/actuator/prometheus>
- RabbitMQ Management: <http://localhost:15672>
- Cobertura JaCoCo: `target/site/jacoco/index.html`

## Configuração

| Variável | Valor padrão |
| --- | --- |
| `SERVER_PORT` | `8080` |
| `MONGODB_URI` | `mongodb://localhost:27017/billing?replicaSet=rs0` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USERNAME` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |
| `RABBITMQ_EXCHANGE` | `mecanica.events` |
| `RABBITMQ_DLX` | `mecanica.events.dlx` |
| `OUTBOX_POLL_INTERVAL_MS` | `1000` |
| `OUTBOX_MAX_ATTEMPTS` | `10` |
| `MERCADO_PAGO_BASE_URL` | `https://api.mercadopago.com` |
| `MERCADO_PAGO_SANDBOX` | `true` |
| `MERCADO_PAGO_ACCESS_TOKEN` | sem valor |
| `MERCADO_PAGO_WEBHOOK_SECRET` | sem valor |
| `MERCADO_PAGO_NOTIFICATION_URL` | sem valor |
| `MERCADO_PAGO_SUCCESS_URL` | sem valor |
| `MERCADO_PAGO_FAILURE_URL` | sem valor |
| `MERCADO_PAGO_PENDING_URL` | sem valor |
| `MERCADO_PAGO_RECONCILIATION_ENABLED` | `false` |

Credenciais reais não devem ser versionadas.

## Qualidade

O comando `./mvnw verify` executa 68 testes (incluindo integração com
Testcontainers — MongoDB + RabbitMQ — e um cenário BDD em Cucumber) e
reprova o build se a cobertura de linhas ficar abaixo de 80%. No último
incremento validado:

- 68 testes, sem falhas, erros ou skips;
- aproximadamente 89,6% de cobertura de linhas;
- relatório JaCoCo local em `target/site/jacoco/index.html`;

## Observabilidade

- Toda resposta HTTP devolve `X-Correlation-Id` e o filtro propaga o valor
  recebido ou gera um novo.
- O `correlationId` viaja no header AMQP das mensagens da Outbox e é gravado
  no MDC dos listeners (`correlationId`, `eventId`).
- Métricas Micrometer expostas em `/actuator/prometheus`:
  `billing.events.enqueued`, `billing.outbox.publish`,
  `billing.outbox.failed` e `billing.messaging.consumed`.

## Integração e Saga

O Billing participa de uma Saga coreografada por eventos no RabbitMQ. O fluxo
implementado cobre orçamento e pagamento sem acessar o banco do OS Service.
Inbox, Outbox, publisher confirms, retry e DLQ garantem processamento pelo
menos uma vez com efeitos idempotentes.

- [Decisão arquitetural da Saga](docs/architecture/ADR-001-saga-coreografada.md)
- [Fluxo, topologia e compensações](docs/architecture/SAGA-BILLING.md)
- [Contrato AsyncAPI](docs/contracts/asyncapi.yaml)
- [Exemplos de eventos](docs/contracts/examples)
- [Integração Mercado Pago](docs/integrations/MERCADO-PAGO.md)

O link de pagamento é criado somente após a aprovação do orçamento, antes da
execução do serviço.

## Planejamento

- [Entregáveis por etapa](docs/ENTREGAVEIS.md)
- [CI/CD e governança do pipeline](docs/CI-CD.md)
- [Análise completa e checklist da Fase 4](docs/FASE4-CHECKLIST.md)
- [Evidências das entregas](docs/ENTREGAS-EVIDENCIAS.md)

Pendências reais para as próximas etapas:

- executar o teste controlado no sandbox do Mercado Pago com as credenciais da
  equipe;
- implementar o BDD cross-service e o cenário de falha/compensação com os
  demais serviços;
- configurar e publicar o quality gate do Sonar, scans de dependências,
  secrets e imagem, e executar publicação/deploy do Kubernetes;
- integrar métricas, traces e dashboard/alertas às ferramentas da Fase 3.
