# Provisionamento externo do Billing Service

Checklist do que falta fornecer/configurar para fechar os itens que não podem
ser validados apenas dentro deste repositório. Nada aqui é evidência de execução:
cada item só vira evidência depois de executado e registrado.

## 1. Mercado Pago sandbox

Necessário do grupo/equipe Mercado Pago:

- aplicação de teste e credenciais de sandbox;
- usuário comprador e usuário vendedor de teste;
- `MERCADO_PAGO_ACCESS_TOKEN` e `MERCADO_PAGO_WEBHOOK_SECRET` via `.env.local`;
- `MERCADO_PAGO_NOTIFICATION_URL` HTTPS pública apontando para
  `/api/v1/payments/webhooks/mercado-pago`;
- `MERCADO_PAGO_SUCCESS_URL`, `MERCADO_PAGO_FAILURE_URL` e
  `MERCADO_PAGO_PENDING_URL` se o grupo quiser telas próprias de retorno.

Execução reproduzível:

```bash
cp .env.example .env.local   # preencher sem commitar
docker compose up -d --wait
./mvnw spring-boot:run
```

Depois: criar/aprovar orçamento, `POST /api/v1/payments`, concluir checkout com
o comprador de teste e confirmar um único `billing.payment.approved.v1`.

Evidência esperada: preferência criada, webhook assinado aceito, consulta ao
provedor aprovada e evento publicado — sem tokens, assinatura ou dados de cartão.

## 2. SonarCloud / quality gate

Estado confirmado: o SonarCloud reconhece o projeto
`diogocarpio_mecanica-billing-service`, mas reporta conflito entre Automatic
Analysis e CI analysis. O workflow já foi alinhado ao padrão do
`os-service-ci-cd` (JDK 21 no scanner, quality gate aguardado só em PR/`main`,
skip com warning quando `SONAR_TOKEN` está vazio), portanto o bloqueio
restante é do lado do SonarCloud.

Necessário:

- administrador do SonarCloud desativar **Automatic Analysis** no projeto
  (método de análise CI-based) e resolver a inconsistência do projeto/painel;
- `SONAR_ENABLED=true`;
- `SONAR_PROJECT_KEY=diogocarpio_mecanica-billing-service`;
- `SONAR_HOST_URL=https://sonarcloud.io` se for SonarCloud;
- `SONAR_ORGANIZATION` da organização SonarCloud;
- secret `SONAR_TOKEN` com permissão de análise.

Evidência esperada: job `Sonar quality gate` executa `sonar:sonar`, aguarda o
quality gate e termina aprovado/reprovado conforme o painel — sem contornar o gate.

## 3. GitHub Actions, GHCR e Kubernetes staging

Necessário:

- `KUBERNETES_DEPLOY_ENABLED=true` somente quando o ambiente estiver pronto;
- environment `staging` com secret `KUBE_CONFIG_BASE64` de uma service account
  restrita ao namespace `mecanica`;
- namespace `mecanica`, Secret `billing-service-secrets`, MongoDB replica set e
  RabbitMQ acessíveis pelo cluster;
- pacote GHCR público ou `imagePullSecret` para o kubelet.

Ordem já documentada em `docs/CI-CD.md`: provisionar dependências → configurar
vars/secrets → rodar `Billing Release` em `main` → verificar rollout e health →
rollback manual se necessário.

Evidência esperada: imagem `ghcr.io/<repo>@sha256:<digest>`, rollout concluído e
`/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`
respondendo.

## 4. BDD cross-service

Necessário do grupo:

- OS Service e serviço de execução disponíveis;
- RabbitMQ com exchange/DLX e contratos implantados;
- ambiente compartilhado ou pipeline de integração acordado.

Cenários propostos em `docs/bdd/BILLING-BDD.md`: fluxo feliz
`os.quote.requested.v1` → orçamento → pagamento aprovado → OS atualizada; e
falha/compensação com `os.cancelled.v1`, `billing.quote.failed.v1`, retry e DLQ.

Evidência esperada: execução real dos dois cenários sem acesso cruzado a bancos.

## 5. Observabilidade da Fase 3

Necessário:

- endpoint/stack Prometheus, Grafana e tracing definidos pelo grupo;
- dashboard e alertas para `billing.outbox.failed`,
  `billing.messaging.consumed`, `billing.outbox.publish` e saúde do serviço.

Evidência esperada: uma OS rastreada pelo mesmo `correlationId` e falha relevante
visível em dashboard/alerta.

## 6. Entrega ao grupo

Necessário:

- dados do Billing para o PDF final;
- ensaio do fluxo feliz e da falha/compensação;
- participação no vídeo de até 15 minutos.

Menor sequência para destravar: resolver SonarCloud → executar sandbox Mercado
Pago → provisionar CI/Kubernetes staging → rodar BDD cross-service → integrar
observabilidade e ensaiar a demonstração.
