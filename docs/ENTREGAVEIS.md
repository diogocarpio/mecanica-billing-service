# Entregáveis por etapa

Este roteiro divide a entrega do Billing Service em incrementos demonstráveis.
Um item só deve ser marcado como concluído depois de validado.

## Progresso executivo

- [x] ETAPA 1 - Análise e planejamento exclusivo do Billing Service.
- [x] ETAPA 2 - Fundação Spring Boot, MongoDB, RabbitMQ e ambiente local.
- [x] ETAPA 3 - MVP de orçamento com API, persistência e testes.
- [x] ETAPA 4 - Contratos de integração e participação do Billing na Saga.
- [x] ETAPA 5 - Mensageria confiável com idempotência, retry e DLQ.
- [ ] ETAPA 6 - Pagamentos e integração sandbox com Mercado Pago.
- [ ] ETAPA 7 - Testes, cobertura de 80%, qualidade e participação no BDD.
- [ ] ETAPA 8 - Observabilidade do Billing Service.
- [ ] ETAPA 9 - Dockerfile e manifests Kubernetes do Billing Service.
- [ ] ETAPA 10 - CI/CD e proteção da branch do repositório Billing.
- [x] ETAPA 11 - Swagger, arquitetura e evidências do Billing Service.

Contagem histórica deste plano: **6 de 11 etapas concluídas (55%)**.
Para a avaliação atual, restrita às exigências do Aluno 2, consultar
[FECHAMENTO-ALUNO-2.md](FECHAMENTO-ALUNO-2.md). As etapas amplas abaixo
incluem decisões opcionais e pendências do grupo; não representam o percentual
atual de entrega do Billing.

Legenda:

- `[x]` concluído;
- `[ ]` pendente;
- itens não decididos pelo grupo permanecem pendentes mesmo que exista uma
  recomendação técnica.

## Marco 0 - Escopo e contratos

**Entrega:** fronteiras do serviço e contratos acordados entre os integrantes.

- [x] Analisar os requisitos oficiais da Fase 4.
- [x] Delimitar responsabilidades do Billing Service.
- [x] Mapear riscos, dependências e critérios de aceite.
- [ ] Confirmar MongoDB no Billing e banco SQL em outro serviço.
- [x] Adotar Saga coreografada no escopo do Billing.
- [x] Definir o pagamento após a aprovação do orçamento e antes da execução.
- [ ] Definir estados oficiais da OS e eventos entre serviços.
- [ ] Definir a origem dos itens e preços do orçamento.
- [x] Publicar contrato AsyncAPI e exemplos JSON para revisão do grupo.

**Aceite:** OS, Billing e execução conseguem evoluir sem banco ou modelo
interno compartilhado.

## Marco 1 - Fundação executável

**Entrega:** aplicação Spring Boot compilável, configuração externa e ambiente
local de dependências.

- [x] Criar projeto Maven com Java 17 e Spring Boot 3.x.
- [x] Adicionar Maven Wrapper.
- [x] Configurar Web, Validation, MongoDB, RabbitMQ, Actuator e OpenAPI.
- [x] Externalizar conexão, porta e credenciais por variáveis de ambiente.
- [x] Criar ambiente local para MongoDB e RabbitMQ com Docker Compose.
- [x] Expor health check e métricas Prometheus.
- [x] Validar `./mvnw verify`.
- [x] Validar inicialização com as dependências locais.

**Aceite:** o projeto compila, os testes passam e o health check responde com
as dependências disponíveis.

## Marco 2 - MVP de orçamento

**Entrega:** primeiro fluxo funcional do Billing, do rascunho à decisão.

- [x] Modelar orçamento, item, tipo e estados.
- [x] Calcular valores com `BigDecimal`.
- [x] Validar itens, moeda, prazo e transições.
- [x] Persistir orçamento no MongoDB com índice único por OS.
- [x] Implementar criação e consulta por ID ou OS.
- [x] Implementar envio para aprovação.
- [x] Implementar aprovação e rejeição.
- [x] Padronizar respostas de erro.
- [x] Documentar endpoints com OpenAPI.
- [x] Validar o fluxo com MongoDB real.
- [ ] Definir e implementar versionamento de orçamento com o grupo.

**Aceite:** uma OS possui orçamento consistente e apenas transições válidas são
persistidas.

## Marco 3 - Mensageria e participação na Saga

**Entrega:** integração assíncrona confiável com o OS Service.

- [x] Versionar envelope e payloads dos eventos.
- [x] Documentar Saga, sequência, topologia e limites do Billing.
- [x] Definir routing keys, filas, DLQ e regras de compatibilidade.
- [x] Configurar exchange, filas e routing keys.
- [x] Consumir solicitação/cancelamento da OS de forma idempotente.
- [x] Publicar eventos de orçamento criado, enviado, aprovado e rejeitado.
- [x] Implementar Inbox para deduplicação.
- [x] Implementar Outbox para publicação confiável.
- [x] Configurar retry com backoff e DLQ.
- [x] Propagar `eventId`, `correlationId` e `causationId`.
- [x] Documentar falhas e compensações do orçamento.
- [x] Implementar cancelamento como compensação do orçamento.
- [x] Testar duplicidade, indisponibilidade, retry e DLQ.

**Aceite:** mensagens repetidas não duplicam efeitos e eventos persistidos não
são perdidos quando o broker fica indisponível.

## Marco 4 - Pagamento e Mercado Pago

**Entrega:** cobrança sandbox segura e confirmação assíncrona.

- [x] Modelar pagamento e sua máquina de estados.
- [x] Impedir pagamento para orçamento não aprovado.
- [x] Criar adapter da API do Mercado Pago.
- [x] Criar cobrança/preferência com chave de idempotência.
- [x] Persistir referência externa e dados seguros de checkout.
- [x] Implementar webhook e validar sua assinatura.
- [x] Consultar o pagamento no provedor antes de confirmar.
- [x] Tratar notificações duplicadas e fora de ordem.
- [x] Publicar eventos de pagamento pendente, aprovado, rejeitado e falho.
- [x] Implementar reconciliação de pagamentos pendentes.
- [ ] Implementar estorno apenas se aprovado como compensação.
- [ ] Validar o fluxo no sandbox.

**Aceite:** uma cobrança é criada uma única vez e somente a confirmação
verificada no Mercado Pago avança a OS.

## Marco 5 - Testes, cobertura e BDD

**Entrega:** qualidade automatizada e evidência de cobertura.

- [x] Configurar JaCoCo com gate mínimo de 80%.
- [x] Criar testes unitários iniciais de domínio, aplicação e API.
- [x] Manter cobertura acima de 80% (92,33% de linhas e 65,68% de branches no
      último `verify` local).
- [x] Testar Inbox, Outbox, publisher confirms, retry e listeners.
- [x] Adicionar testes de integração com MongoDB e RabbitMQ (Testcontainers).
- [x] Testar contratos HTTP e de eventos (MockMvc standalone +
      `HttpContractTest` e `EventContractTest` sobre os exemplos JSON).
- [x] Testar o adapter Mercado Pago sem produção.
- [x] Implementar cenário BDD executável do Billing (Cucumber).
- [ ] Implementar cenário BDD ponta a ponta com os demais serviços.
- [x] Implementar cenário BDD de falha/compensação (local, Cucumber:
      `billing-saga-failure.feature`).
- [x] Configurar scanner Sonar (`sonar-project.properties` + plugin Maven).
- [ ] Publicar evidências de cobertura e quality gate no CI.

**Aceite:** o pipeline bloqueia cobertura inferior a 80% ou quality gate
reprovado, e o BDD comprova o fluxo distribuído.

## Marco 6 - Observabilidade

**Entrega:** rastreamento operacional de uma OS entre serviços.

- [x] Expor health check e endpoint Prometheus.
- [x] Estruturar logs com `correlationId`/`eventId` no MDC, sem dados sensíveis.
- [x] Propagar `correlationId` por HTTP (`X-Correlation-Id`), RabbitMQ (header)
      e logs.
- [ ] Integrar métricas, logs e traces às ferramentas da Fase 3.
- [x] Medir eventos enfileirados, publicações na Outbox, falhas definitivas e
      consumo por tipo de evento.
- [ ] Criar dashboard e alertas mínimos.
- [x] Documentar reprocessamento seguro da DLQ
      ([docs/DLQ-REPROCESSAMENTO.md](DLQ-REPROCESSAMENTO.md)).

**Aceite:** uma OS é localizada de ponta a ponta por um mesmo identificador e
uma falha aparece no monitoramento.

## Marco 7 - Container, Kubernetes e infraestrutura

**Entrega:** serviço reproduzível e implantável no cluster.

- [x] Criar Dockerfile multi-stage e executar como usuário não root.
- [x] Criar `.dockerignore`.
- [x] Criar Deployment e Service.
- [x] Configurar ConfigMap e referências a Secret.
- [x] Configurar liveness, readiness, recursos e rollout.
- [ ] Configurar Ingress somente para endpoints públicos.
- [ ] Definir persistência ou serviço gerenciado do MongoDB.
- [ ] Criar Terraform acordado para a infraestrutura (se adotado pelo grupo).
- [ ] Validar manifests em um cluster.

**Aceite:** uma imagem imutável sobe no Kubernetes, passa nas probes e não
contém credenciais.

## Marco 8 - CI/CD e governança

**Entrega:** pipeline independente, da validação do PR ao deploy.

Os workflows `Billing CI` e `Billing Release` foram criados
(`.github/workflows/`). O Billing CI passou no GitHub Actions para o commit
`1b90fff`, com 68 testes, gate JaCoCo, validação de manifests e build da imagem.
Sonar, publicação e deploy continuam pendentes conforme
[docs/CI-CD.md](CI-CD.md).

- [x] Executar build, testes e gate de cobertura no GitHub Actions.
- [ ] Criar CI de PR com build, testes, cobertura e qualidade
      (build validado por push; quality gate do Sonar depende de variáveis do
      repositório ainda não provisionadas).
- [x] Adicionar scans de dependências, secrets e imagem
      (Trivy não bloqueante no job `image` do CI; evidência pendente da
      próxima execução remota; quality gate formal ainda depende de
      decisão).
- [ ] Publicar imagem versionada em registry
      (job criado; depende de execução do release no GitHub).
- [ ] Automatizar deploy no Kubernetes
      (job criado, opt-in via `KUBERNETES_DEPLOY_ENABLED`; depende do
      ambiente `staging` e do secret `KUBE_CONFIG_BASE64`).
- [ ] Separar secrets por ambiente.
- [ ] Configurar rollback ou rollout seguro
      (rollback manual documentado; sem reversão automática).
- [x] Proteger `main` com PR e checks obrigatórios.
- [x] Registrar evidência de execução bem-sucedida do CI.
- [ ] Registrar evidência de publicação, quality gate Sonar e deploy.

**Aceite:** código reprovado não entra na principal e código aprovado gera
imagem e deploy rastreáveis.

## Marco 9 - Documentação e demonstração

**Entrega:** repositório auditável e material para a entrega do grupo.

- [x] Atualizar README com arquitetura, execução e variáveis.
- [x] Documentar e justificar a estratégia Saga do Billing.
- [x] Publicar o diagrama de sequência do trecho de orçamento.
- [x] Manter Swagger ou Postman atualizado.
- [x] Reunir evidências de cobertura, qualidade, deploy e observabilidade
      disponíveis e registrar pendências em
      [docs/ENTREGAS-EVIDENCIAS.md](ENTREGAS-EVIDENCIAS.md).
- [ ] Entregar ao grupo os dados do Billing para o PDF final.
- [ ] Ensaiar fluxo feliz completo.
- [ ] Ensaiar falha, retry/DLQ e compensação.
- [ ] Participar do vídeo de até 15 minutos.

**Aceite:** outra pessoa executa, integra e demonstra o Billing usando somente
o repositório e suas evidências.

## Caminho crítico

1. Concluir decisões da Etapa 0.
2. Validar as Etapas 1 e 2.
3. Implementar Saga e mensageria da Etapa 3.
4. Implementar Mercado Pago da Etapa 4.
5. Fechar testes e BDD da Etapa 5.
6. Concluir operação, infraestrutura, CI/CD e evidências.
