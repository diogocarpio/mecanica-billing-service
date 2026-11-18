# Progresso da entrega — Billing Service (Aluno 2)

Para o fechamento com prazo curto, usar o
[recorte dos requisitos oficiais do Aluno 2](FECHAMENTO-ALUNO-2.md).
Os seis itens abaixo são frentes de trabalho, não uma medida percentual.

Rastreio dos 6 itens restantes para considerar o trabalho entregue, em ordem
de execução. Cada item só é marcado como concluído com evidência verificável;
checklists de provisionamento não contam como execução.

Legenda: `[ ]` pendente · `[~]` parcialmente pronto / aguardando dependência
externa · `[x]` concluído com evidência.

## 1/6 — SonarCloud / quality gate

- [x] Workflow alinhado ao padrão `os-service-ci-cd` (JDK 21 no scanner,
  `SONAR_QG_WAIT` só em PR/`main`, skip com warning sem `SONAR_TOKEN`,
  sem `sonar.ci.autoconfig.disabled`).
- [x] `sonar-project.properties` com `projectKey` igual ao projeto real
  `diogocarpio_mecanica-billing-service`.
- [x] Última execução do CI remota verificada: run `44011879516` (commit
  `2989693`) — `Build and tests`, `Kubernetes manifests` e `Container build`
  aprovados; `Sonar quality gate` **skipped** por `SONAR_ENABLED` ausente.
- [~] Pendente externo: admin desativar Automatic Analysis no SonarCloud;
  vars `SONAR_ENABLED`/`SONAR_PROJECT_KEY`/`SONAR_HOST_URL`/`SONAR_ORGANIZATION`
  e secret `SONAR_TOKEN`; novo push para executar o job e conferir o gate.

Detalhes: [PROVISIONAMENTO.md](PROVISIONAMENTO.md) seção 2,
[CI-CD.md](CI-CD.md).

## 2/6 — Fluxo de pagamento com provedor simulado

- [x] Simulador local `tools/payment-simulator` implementa o contrato
  Mercado Pago usado pelo `MercadoPagoHttpGateway`: Bearer, criação
  idempotente de preferência, consulta por ID/`external_reference`,
  checkout tokenizado, webhook HMAC-SHA256 e retry.
- [x] Integração via Compose (`payment-simulator`, porta 4010) e variáveis
  `MERCADO_PAGO_BASE_URL`, `MERCADO_PAGO_ACCESS_TOKEN` e
  `MERCADO_PAGO_WEBHOOK_SECRET` documentadas.
- [x] Execução local ponta a ponta verificada em 2026-11-18: orçamento
  aprovado → `POST /api/v1/payments` → checkout simulado → webhook assinado →
  consulta ao provedor → `APPROVED`; recusa → `REJECTED`; reenvio de webhook
  não alterou estado terminal.
- [ ] Sandbox real do Mercado Pago permanece opcional se o grupo tiver
  aplicação de teste e URL pública HTTPS.

Detalhes: [PROVISIONAMENTO.md](PROVISIONAMENTO.md) seção 1,
[integrations/MERCADO-PAGO.md](integrations/MERCADO-PAGO.md).

## 3/6 — CI/CD remoto: Trivy, GHCR e Kubernetes staging

- [ ] Primeira execução remota dos scans Trivy (artefato `security-scans`).
- [ ] GHCR: imagem publicada com digest imutável (release na `main`).
- [ ] Staging: `KUBERNETES_DEPLOY_ENABLED=true`, environment `staging`,
  `KUBE_CONFIG_BASE64`, namespace/Secret/Mongo/Rabbit no cluster;
  rollout + probes validados.

Detalhes: [PROVISIONAMENTO.md](PROVISIONAMENTO.md) seção 3,
[CI-CD.md](CI-CD.md), [release.yml](../.github/workflows/release.yml).

## 4/6 — BDD cross-service

- [ ] Ambiente compartilhado com OS Service + Execution Service + RabbitMQ.
- [ ] Happy path: `os.quote.requested.v1` → orçamento → pagamento aprovado →
  OS atualizada.
- [ ] Falha/compensação: `os.cancelled.v1`, `billing.quote.failed.v1`,
  retry e DLQ — sem acesso cruzado a bancos.

Detalhes: [bdd/BILLING-BDD.md](bdd/BILLING-BDD.md),
[PROVISIONAMENTO.md](PROVISIONAMENTO.md) seção 4.

## 5/6 — Observabilidade integrada (Fase 3)

- [ ] Prometheus/Grafana/tracing do grupo apontando para
  `/actuator/prometheus` do Billing.
- [ ] Dashboards/alertas das métricas `billing.*` (outbox, consumed,
  enqueued, health).
- [ ] Demo com um `correlationId` atravessando serviços e uma falha visível.

Detalhes: [PROVISIONAMENTO.md](PROVISIONAMENTO.md) seção 5.

## 6/6 — Entrega ao grupo

- [ ] Dados do Billing para o PDF final.
- [ ] Ensaio do happy path e da compensação.
- [ ] Vídeo de até 15 min: orçamento → aprovação → checkout → webhook →
  evento → idempotência/compensação → métricas.

Detalhes: [ENTREGAS-EVIDENCIAS.md](ENTREGAS-EVIDENCIAS.md) (roteiro de demo),
[PROVISIONAMENTO.md](PROVISIONAMENTO.md) seção 6.
