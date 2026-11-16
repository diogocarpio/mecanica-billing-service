# Evidências das entregas do Billing Service

Este documento consolida o que foi implementado no Billing Service, como
executar e quais evidências verificáveis existem — além das pendências que
permanecem abertas. Ele não declara entrega ao grupo nem participação no
vídeo; apenas documenta o estado do repositório.

## Escopo e fontes de verdade

Fontes oficiais do repositório:

- [README](../README.md) — estado atual, stack, execução local, endpoints,
  container/Kubernetes, configuração, qualidade e observabilidade.
- [ADR-001 — Saga coreografada](architecture/ADR-001-saga-coreografada.md) —
  decisão arquitetural de integração por eventos.
- [SAGA-BILLING](architecture/SAGA-BILLING.md) — fluxo, topologia de
  RabbitMQ, compensações e limites do Billing.
- [Contrato AsyncAPI](contracts/asyncapi.yaml) e
  [exemplos de eventos](contracts/examples) — contrato assíncrono versionado.
- [Integração Mercado Pago](integrations/MERCADO-PAGO.md) — checkout, webhook,
  reconciliação e segurança.
- [CI/CD](CI-CD.md) — pipelines, governança e pendências do pipeline.
- [BDD](bdd/BILLING-BDD.md) — cenários Cucumber executáveis do Billing.

## Funcionalidades implementadas

- **Orçamento:** criação, consulta por ID/OS, envio para aprovação,
  aprovação/rejeição, cálculo com `BigDecimal`, transições e duplicidade por
  OS validadas, persistência no MongoDB próprio.
- **Saga e mensageria:** consumo idempotente de solicitação/cancelamento via
  RabbitMQ (topic exchange + DLX), Inbox para deduplicação, Outbox com
  confirmação do broker, retry com backoff e encaminhamento de falhas
  definitivas para DLQ, propagação de `correlationId`, `eventId` e
  `causationId`.
- **Pagamento/Mercado Pago:** criação idempotente de preferência somente para
  orçamento aprovado, webhook com validação de assinatura, consulta do
  pagamento no provedor antes de confirmar, tratamento de notificações
  duplicadas/fora de ordem, reconciliação de pendentes e eventos de pagamento
  via Outbox.
- **Observabilidade:** `X-Correlation-Id` HTTP, header AMQP e MDC nos
  listeners, métricas Micrometer em `/actuator/prometheus`
  (`billing.events.enqueued`, `billing.outbox.publish`,
  `billing.outbox.failed`, `billing.messaging.consumed`).
- **Container/Kubernetes:** Dockerfile multi-stage com usuário não root,
  probes de liveness/readiness, manifests em `k8s/` (namespace, configmap,
  secret com placeholders, deployment e service).
- **CI/CD:** workflow `Billing CI` no GitHub Actions com testes, gate JaCoCo,
  kubeconform e build da imagem; `main` protegida com PR e checks obrigatórios.

## Como executar localmente

Comandos já documentados no README (nenhuma configuração adicional):

```bash
docker compose up -d --wait   # sobe MongoDB (replica set) e RabbitMQ
./mvnw clean verify           # compila, executa 68 testes e valida JaCoCo >= 80%
./mvnw spring-boot:run        # inicia a aplicação em :8080
```

Verificação rápida do container:

```bash
docker build -t mecanica/billing-service:local .
docker run -p 8080:8080 \
  -e MONGODB_URI='mongodb://...' \
  -e RABBITMQ_HOST='...' \
  mecanica/billing-service:local
curl http://localhost:8080/actuator/health
```

## Matriz de evidências verificáveis

| Requisito | Evidência | Status |
| --- | --- | --- |
| Build, testes e cobertura no CI | [Billing CI — run 43959216320](https://github.com/alexandrasoares/mecanica-billing-service/actions/runs/43959216320) (commit `1b90fff`): 68 testes, 0 falhas/erros/skips, gate JaCoCo aprovado | Verificado |
| CI em commit posterior | [Billing CI — run 43960161227](https://github.com/alexandrasoares/mecanica-billing-service/actions/runs/43960161227) (commit `f1eadb4`): success | Verificado |
| Artefato de evidências de teste | Artefato `test-evidence` publicado pelo workflow Billing CI no run 43959216320 | Verificado |
| Validação de manifests Kubernetes | kubeconform aprovou 5 manifests no CI (run 43959216320) | Verificado em CI |
| Build de imagem no CI | Job `Container build` do workflow Billing CI | Verificado em CI |
| Imagem Docker local | `mecanica/billing-service:local` construída; container respondeu health UP com liveness/readiness | Verificado localmente |
| Cobertura JaCoCo local | `target/site/jacoco/index.html` e `target/surefire-reports` (~89,6% de linhas, gate >=80% aprovado) | Verificado localmente |
| Contrato e exemplos de eventos | `docs/contracts/asyncapi.yaml` e `docs/contracts/examples` | Verificado |
| BDD do Billing | Cenário Cucumber local aprovado (documentado em `docs/bdd/BILLING-BDD.md`) | Verificado localmente |
| Quality gate Sonar | Workflow preparado, mas análise não executada (`SONAR_ENABLED`/variáveis não provisionadas) | Pendente |
| Scans de dependências, secrets e imagem | Jobs não implementados | Pendente |
| Publicação GHCR e deploy Kubernetes | Jobs criados, opt-in; nunca executados; secrets/ambiente staging não provisionados | Pendente |
| Sandbox real Mercado Pago | Implementação e testes com provedor controlado aprovados; teste com credenciais reais de sandbox pendente (depende da aplicação Mercado Pago da equipe) | Pendente |
| BDD cross-service e falha/compensação | Apenas cenário local do Billing existe | Pendente |
| Observabilidade integrada | correlationId, MDC e métricas implementados; integração com ferramentas da Fase 3, dashboard e alertas pendentes | Parcial |
| Validação de manifests em cluster | kubeconform validou sintaxe; deploy real não validado | Pendente |

## Dados para o PDF do grupo

- **Repositório:** <https://github.com/alexandrasoares/mecanica-billing-service>
- **Responsabilidade do serviço:** dono do domínio de orçamento e pagamento
  das ordens de serviço — criação, aprovação/rejeição do orçamento e cobrança
  via Mercado Pago após a aprovação, antes da execução.
- **Stack:** Java 17, Spring Boot 3.5, Maven, Springdoc OpenAPI, Actuator +
  Micrometer/Prometheus, Docker.
- **Banco NoSQL próprio:** MongoDB (replica set), com índices únicos por OS,
  Inbox e Outbox.
- **Comunicação:** REST para a API do serviço; RabbitMQ (topic exchange + DLX)
  para eventos da Saga.
- **Saga:** coreografada por eventos, sem acesso a bancos de outros serviços;
  idempotência, retry, DLQ e compensações documentadas.
- **Pagamento:** link de checkout do Mercado Pago criado apenas após
  aprovação do orçamento; webhook assinado + consulta ao provedor confirmam o
  pagamento de forma idempotente.
- **Testes e cobertura:** 68 testes no `./mvnw verify` (unitários, integração
  Testcontainers e Cucumber), gate JaCoCo >=80% com ~89,6% de linhas
  verificado no CI e localmente.
- **Observabilidade:** correlationId propagado em HTTP/RabbitMQ, MDC nos
  listeners e métricas Prometheus.
- **Container/Kubernetes/CI-CD:** Dockerfile multi-stage não root, manifests
  em `k8s/` validados por kubeconform no CI, branch `main` protegida e
  workflow Billing CI aprovado.
- **Pendências:** sandbox real do Mercado Pago, BDD cross-service e de
  falha/compensação, quality gate Sonar e scans, publicação GHCR/deploy
  Kubernetes, dashboard/alertas/tracing e validação dos manifests em cluster.

## Roteiro mínimo de demonstração do Billing

> Depende do ambiente e dos serviços do grupo (OS Service, broker, front de
> aprovação e Mercado Pago). Sem esse ambiente, a demonstração limita-se ao
> trecho local do Billing.

**Fluxo feliz:**

1. Criar orçamento via `POST /api/v1/quotes` (ou consumir
   `os.quote.requested.v1` no RabbitMQ).
2. Enviar para aprovação e aprovar via API.
3. Criar pagamento via `POST /api/v1/payments` e obter o link de checkout do
   Mercado Pago sandbox.
4. Simular webhook assinado e mostrar a confirmação idempotente do pagamento
   no provedor e os eventos publicados pela Outbox.

**Falha e compensação:**

1. Rejeitar o orçamento ou enviar `os.cancelled.v1` e mostrar a
   compensação/idempotência.
2. (Quando implementado) forçar falha transitória, demonstrar retry e o
   encaminhamento à DLQ, e reprocessar com segurança.

**Observabilidade durante a demo:** acompanhar `X-Correlation-Id`, logs com
`correlationId`/`eventId` e métricas em `/actuator/prometheus`.
