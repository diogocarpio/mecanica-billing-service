# Fase 4 - Plano de entrega do Billing Service

## 1. Objetivo e recorte

O Billing Service deve ser um microsserviço independente responsável por:

- gerar e manter o orçamento de uma ordem de serviço (OS);
- disponibilizar e enviar o orçamento para aprovação;
- registrar a aprovação ou rejeição do orçamento;
- iniciar, registrar e verificar pagamentos no Mercado Pago;
- comunicar ao OS Service os resultados de orçamento e pagamento;
- participar da Saga da OS, incluindo falhas, repetição segura e compensações.

Não fazem parte deste serviço:

- manter a OS ou alterar diretamente seu banco;
- gerenciar clientes, veículos, estoque ou execução do reparo;
- decidir sozinho o fluxo global da Saga;
- armazenar credenciais do Mercado Pago no código ou nos manifests.

Quando o requisito diz "atualizar o status da OS", o Billing deve solicitar a
atualização por evento ou API do OS Service. Ele nunca deve escrever no banco
de outro serviço.

## 2. Diagnóstico do repositório

Situação em 15/11/2026:

| Área | Situação atual | Lacuna |
| --- | --- | --- |
| Código-fonte | Ausente | Criar a aplicação Spring Boot |
| Domínio | Apenas responsabilidades no README | Implementar orçamento, itens, pagamento e estados |
| Banco próprio | MongoDB citado no README | Configurar, modelar, indexar e versionar dados |
| API | Ausente | Criar API REST e OpenAPI/Swagger |
| Mercado Pago | Ausente | Criar checkout, webhook, consulta e idempotência |
| Integração | RabbitMQ citado no README | Definir contratos, filas, eventos, retry e DLQ |
| Saga | Intenção de orquestração no README | Escolher estratégia com o grupo, implementar e documentar |
| Testes | Ausentes | Unitários, integração, contrato e participação no BDD |
| Cobertura | Ausente | JaCoCo com mínimo de 80% e evidência |
| Qualidade | Ausente | SonarQube/SonarCloud e quality gate |
| Container | Ausente | Dockerfile e execução local |
| Kubernetes | Ausente | Manifests, configuração, secrets, probes e recursos |
| CI/CD | Ausente | Build, testes, qualidade, imagem e deploy |
| Observabilidade | Ausente | Métricas, logs correlacionados e tracing distribuído |
| Documentação | README inicial | Arquitetura, operação, Saga, APIs e evidências |

O repositório contém somente `README.md` e `.gitignore`. Portanto, a entrega do
Aluno 2 ainda está em fase de planejamento.

> Nota de auditoria: a tabela acima é o diagnóstico histórico de 15/11/2026.
> O estado atual verificado está em `docs/ENTREGAVEIS.md` e
> `docs/ENTREGAS-EVIDENCIAS.md`; vários itens implementados depois ainda
> aparecem como pendentes nos Marcos detalhados abaixo até a próxima revisão.

## 3. Decisões que precisam ser alinhadas com o grupo

Estas definições bloqueiam os contratos entre serviços e devem ser fechadas
antes da implementação das integrações:

- [ ] Confirmar que o Billing usará MongoDB e que ao menos outro serviço usará
      banco SQL, atendendo ao requisito global de SQL + NoSQL.
- [ ] Escolher uma única estratégia de Saga para o fluxo do grupo.
- [ ] Definir quem coordena o fluxo global e qual serviço é dono de cada estado.
- [x] Definir o pagamento após a aprovação do orçamento e antes da execução.
- [ ] Definir quem fornece itens, peças, mão de obra, quantidades e preços para
      a geração do orçamento.
- [ ] Definir como o cliente recebe e aprova o orçamento: API, aplicação do
      grupo, link, e-mail ou outro canal.
- [ ] Definir os estados oficiais da OS e quais eventos permitem cada mudança.
- [ ] Definir contratos versionados de eventos, IDs globais, moeda, precisão
      monetária, timestamps e formato de erros.
- [ ] Definir RabbitMQ, exchange, routing keys, filas, retry, DLQ e responsáveis.
- [ ] Definir ambientes, namespace Kubernetes, registry de imagens, DNS e forma
      de gerenciar secrets.
- [ ] Confirmar quais ferramentas da Fase 3 serão reutilizadas para métricas,
      logs e tracing.

### Estratégia recomendada

Com a stack já proposta no README, a recomendação é Saga coreografada com
RabbitMQ:

1. O OS Service publica fatos do domínio.
2. O Billing reage apenas aos eventos relevantes e executa uma transação local.
3. O Billing publica o resultado dessa transação.
4. O OS Service e o serviço de execução reagem e avançam seus próprios estados.
5. Eventos de falha ou compensação desfazem logicamente as etapas possíveis.

Essa opção mantém os serviços desacoplados e combina com RabbitMQ. Se o grupo
escolher orquestração, deve existir um orquestrador claramente definido; o
README não pode chamar consumidores coreografados de "orquestração".

## 4. Modelo de domínio mínimo

### Orçamento

Campos mínimos:

- `id`, `osId`, `customerId` ou referência necessária;
- versão do orçamento;
- itens com descrição, tipo, quantidade, valor unitário e subtotal;
- descontos, impostos se aplicáveis, valor total e moeda;
- status, validade, data de criação e atualização;
- datas de envio, aprovação ou rejeição;
- motivo de rejeição/cancelamento;
- `correlationId` e metadados de auditoria.

Estados recomendados:

`DRAFT -> PENDING_APPROVAL -> APPROVED | REJECTED | EXPIRED | CANCELLED`

Regras essenciais:

- usar `BigDecimal`, nunca `double`, para dinheiro;
- não aceitar total negativo nem item inválido;
- calcular o total no domínio, sem confiar no total enviado pelo cliente;
- impedir aprovação após expiração, rejeição ou cancelamento;
- tornar comandos repetidos idempotentes;
- versionar um orçamento alterado depois de enviado.

### Pagamento

Campos mínimos:

- `id`, `osId`, `quoteId`;
- valor, moeda e status interno;
- ID, status e payload de referência do Mercado Pago;
- chave de idempotência;
- método ou tipo de pagamento quando necessário;
- datas de criação, atualização, aprovação e cancelamento;
- histórico mínimo de mudanças;
- `correlationId` e informações de erro sem dados sensíveis.

Estados recomendados:

`PENDING -> IN_PROCESS -> APPROVED | REJECTED | CANCELLED | ERROR`

Após aprovação, quando aplicável:

`APPROVED -> REFUND_PENDING -> REFUNDED`

Regras essenciais:

- só criar cobrança para orçamento aprovado e ainda não pago;
- o valor cobrado deve vir do orçamento persistido;
- uma OS/orçamento não pode gerar cobranças duplicadas por repetição técnica;
- webhook não é prova suficiente: validar assinatura e consultar o pagamento na
  API do Mercado Pago antes da confirmação local;
- nunca registrar token, assinatura, dados de cartão ou payload sensível em log;
- processar notificações duplicadas e fora de ordem com segurança.

### Confiabilidade da integração

Adicionar:

- caixa de saída (`outbox`) para não perder eventos após gravar o domínio;
- caixa de entrada ou coleção de eventos processados (`inbox`) para deduplicação;
- índice único para chaves de negócio e idempotência;
- retry com backoff para falhas transitórias;
- DLQ para mensagens esgotadas;
- reconciliação periódica de pagamentos pendentes;
- controle otimista de versão para atualizações concorrentes.

## 5. Fluxos que precisam funcionar

### Fluxo de orçamento

1. Billing recebe `os.quote.requested.v1` ou uma requisição interna equivalente.
2. Valida o contrato e deduplica a mensagem.
3. Gera e persiste o orçamento.
4. Marca o orçamento como pendente de aprovação.
5. Publica `billing.quote.created.v1` e `billing.quote.submitted.v1`.
6. Cliente aprova ou rejeita por uma operação idempotente.
7. Billing publica `billing.quote.approved.v1` ou `billing.quote.rejected.v1`.
8. OS Service atualiza a OS sem acesso cruzado a banco.

### Fluxo de pagamento

1. Billing valida que o orçamento está aprovado.
2. Cria o pagamento no Mercado Pago com chave de idempotência.
3. Persiste a referência externa e retorna os dados seguros de checkout.
4. Mercado Pago chama o webhook.
5. Billing valida a origem e consulta o pagamento no provedor.
6. Atualiza o pagamento de forma idempotente.
7. Publica `billing.payment.approved`, `rejected` ou `cancelled`.
8. OS Service atualiza o status da OS ao consumir o evento.

### Falhas e compensações

Documentar uma matriz contendo falha, detecção, retry, compensação e estado
final. Casos mínimos:

| Falha | Tratamento esperado |
| --- | --- |
| Evento de OS duplicado | Inbox ignora o reprocessamento |
| Dados insuficientes para orçamento | Evento de falha; OS não avança |
| Orçamento rejeitado ou expirado | Evento correspondente; execução não inicia |
| RabbitMQ indisponível após persistência | Outbox tenta novamente |
| Mercado Pago indisponível | Retry com limite; pagamento permanece pendente/erro |
| Webhook duplicado ou fora de ordem | Idempotência e transição de estado válida |
| Pagamento aprovado, publicação falha | Outbox garante publicação posterior |
| Mensagem inválida ou retry esgotado | DLQ, alerta e procedimento de reprocessamento |
| OS recusa evento por estado incompatível | Evento de compensação e análise; refund se a regra aprovada exigir |

Uma transação financeira confirmada não deve ser simplesmente "apagada" como
rollback. A compensação financeira é estorno/reembolso, com trilha de auditoria
e regra explícita do grupo.

## 6. Contratos sugeridos

Os nomes finais precisam ser compartilhados e versionados pelo grupo.

### API REST

- `POST /api/v1/quotes` - criar orçamento, se a entrada não for somente evento.
- `GET /api/v1/quotes/{id}` - consultar orçamento.
- `GET /api/v1/quotes?osId={osId}` - consultar por OS.
- `POST /api/v1/quotes/{id}/submit` - enviar para aprovação.
- `POST /api/v1/quotes/{id}/approve` - aprovar.
- `POST /api/v1/quotes/{id}/reject` - rejeitar com motivo.
- `POST /api/v1/payments` - iniciar cobrança.
- `GET /api/v1/payments/{id}` - consultar estado local.
- `POST /api/v1/payments/webhooks/mercado-pago` - receber notificação.
- `POST /api/v1/payments/{id}/refund` - compensar, se aprovado no escopo.

Toda API deve ter validação, erros padronizados, OpenAPI, autenticação definida,
correlation ID e idempotência nas operações de comando.

### Eventos

Consumidos no trecho de orçamento proposto:

- `os.quote.requested.v1`;
- `os.cancelled.v1`;

Publicados:

- `billing.quote.created.v1`;
- `billing.quote.submitted.v1`;
- `billing.quote.approved.v1`;
- `billing.quote.rejected.v1`;
- `billing.quote.cancelled.v1`;
- `billing.quote.failed.v1`;
- `billing.payment.pending.v1`;
- `billing.payment.approved.v1`;
- `billing.payment.rejected.v1`;
- `billing.payment.failed.v1`;
- `billing.payment.refunded.v1`.

Envelope mínimo:

`eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`,
`causationId`, `osId`, `source` e `payload`.

## 7. Checklist de implementação

### Marco 0 - Contratos do grupo

- [ ] Fechar as decisões da seção 3.
- [x] Desenhar a sequência do Billing na Saga.
- [x] Publicar AsyncAPI e exemplos JSON dos eventos de orçamento.
- [x] Definir retries, compensações e estados finais do trecho de orçamento.
- [ ] Definir o cenário BDD ponta a ponta e um cenário de falha.

Critério de aceite: os serviços concordam sobre estados, eventos e IDs sem
depender de banco compartilhado.

### Marco 1 - Fundação da aplicação

- [x] Criar projeto Java 17 + Spring Boot 3.x com Maven Wrapper.
- [x] Adicionar Web, Validation, MongoDB, AMQP, Actuator e OpenAPI.
- [x] Organizar camadas de domínio, aplicação e adaptadores.
- [x] Criar configuração por profiles, sem secrets versionados.
- [x] Implementar tratamento padronizado de erros e correlation ID.
- [ ] Configurar lint/formatação e análise estática.

Critério de aceite: aplicação compila, sobe localmente e responde health check.

### Marco 2 - Orçamento

- [x] Implementar entidades, value objects, estados e regras.
- [x] Implementar cálculo monetário e validações.
- [x] Criar repositórios MongoDB e índices.
- [x] Implementar casos de uso de criar, enviar, consultar, aprovar e rejeitar.
- [x] Expor os endpoints necessários.
- [x] Garantir idempotência e concorrência.
- [x] Escrever testes unitários das regras e transições.

Critério de aceite: fluxo de orçamento funciona localmente e rejeita transições
inválidas.

### Marco 3 - Mensageria e Saga

- [x] Criar exchanges, filas, bindings e routing keys.
- [x] Implementar consumidores idempotentes.
- [x] Implementar publicadores com outbox e confirmação do broker.
- [x] Configurar retry, backoff e DLQ.
- [x] Propagar `correlationId`, `eventId` e `causationId`.
- [x] Implementar eventos de sucesso e cancelamento compensatório.
- [x] Testar duplicidade, indisponibilidade, retry e DLQ.

Critério de aceite: nenhum evento confirmado é perdido e mensagens duplicadas
não duplicam efeitos.

### Marco 4 - Mercado Pago

- [ ] Criar conta/aplicação de teste e usar credenciais de sandbox.
- [x] Implementar adapter isolado para a API do Mercado Pago.
- [x] Criar cobrança/preferência com chave de idempotência.
- [x] Implementar webhook, validação de assinatura e consulta ao provedor.
- [x] Mapear status externo para estados internos.
- [x] Implementar consulta e reconciliação de pagamentos pendentes.
- [ ] Implementar refund somente se fizer parte da compensação aprovada.
- [x] Testar adapter e fluxo completo com provedor HTTP controlado.
- [ ] Executar teste controlado no sandbox.

Critério de aceite: uma notificação válida confirma uma única vez o pagamento e
gera o evento esperado; notificações falsas ou repetidas não alteram o estado.

### Marco 5 - API e documentação técnica

- [x] Documentar endpoints, schemas, erros e autenticação no OpenAPI.
- [x] Manter Swagger UI acessível no ambiente apropriado.
- [ ] Exportar coleção Postman se o grupo a utilizar.
- [x] Criar exemplos de requests, webhooks e eventos do Billing.
- [x] Documentar variáveis de ambiente e execução local.
- [x] Documentar decisões arquiteturais e estratégia Saga no README.

Critério de aceite: outra pessoa consegue executar e integrar o serviço usando
somente a documentação.

### Marco 6 - Testes e qualidade

- [x] Cobrir domínio e casos de uso com testes unitários.
- [x] Testar MongoDB e RabbitMQ com integração real isolada via Testcontainers.
- [x] Testar contratos HTTP e de eventos (MockMvc standalone:
      `HttpContractTest`; exemplos JSON: `EventContractTest`).
- [x] Testar adapter Mercado Pago sem chamar produção.
- [x] Implementar cenário BDD executável do Billing (Cucumber) — ver
      `docs/bdd/BILLING-BDD.md`.
- [ ] Participar de pelo menos um fluxo BDD completo entre microsserviços.
- [x] Incluir ao menos um BDD de falha/compensação para a demonstração
      (cenário local executável: `billing-saga-failure.feature`).
- [x] Configurar JaCoCo com gate mínimo de 80% por serviço.
- [x] Configurar scanner Sonar (`sonar-project.properties` + plugin Maven).
- [ ] Executar análise Sonar e quality gate no CI.
- [ ] Guardar link ou imagem da cobertura no README.

Critério de aceite: pipeline falha abaixo de 80% ou com quality gate reprovado,
e o BDD prova a integração do Billing no fluxo completo.

### Marco 7 - Observabilidade e operação

- [x] Expor liveness, readiness e métricas via Actuator.
- [ ] Integrar métricas à ferramenta usada na Fase 3.
- [x] Emitir logs com `correlationId`/`eventId`, sem secrets ou dados
      financeiros sensíveis.
- [x] Propagar `correlationId` em HTTP, RabbitMQ e chamadas ao Mercado Pago.
- [x] Criar métricas para eventos, publicações, falhas e consumo.
- [ ] Criar dashboard e alertas mínimos.
- [x] Documentar consulta e reprocessamento seguro da DLQ
      ([docs/DLQ-REPROCESSAMENTO.md](DLQ-REPROCESSAMENTO.md)).

Critério de aceite: uma OS pode ser rastreada de ponta a ponta pelo mesmo
identificador e falhas relevantes aparecem no monitoramento.

### Marco 8 - Docker, Kubernetes e infraestrutura

- [x] Criar Dockerfile multi-stage, imagem pequena e usuário não root.
- [x] Criar `.dockerignore`.
- [x] Disponibilizar ambiente local reproduzível para aplicação, MongoDB e
      RabbitMQ.
- [x] Criar Deployment e Service Kubernetes.
- [x] Criar ConfigMap e referências a Secret, sem valores sensíveis no Git.
- [x] Configurar probes, requests/limits, estratégia de rollout e réplicas.
- [ ] Criar Ingress apenas para endpoints que precisam ser públicos.
- [ ] Configurar persistência/serviço gerenciado do MongoDB conforme ambiente.
- [ ] Criar IaC prometida pelo projeto ou ajustar o README ao escopo acordado.
- [ ] Testar os manifests em cluster real ou local.

Critério de aceite: uma imagem imutável sobe no Kubernetes, passa nas probes e
se conecta às dependências sem credenciais embutidas.

### Marco 9 - CI/CD e governança

- [ ] Criar workflow de PR com compilação, testes, cobertura e qualidade.
- [ ] Criar workflow de branch principal com imagem versionada e push no registry.
- [x] Adicionar scan de dependências, secrets e imagem
      (Trivy não bloqueante no job `image` do CI; evidência remota pendente
      do próximo push).
- [ ] Criar deploy automatizado no Kubernetes.
- [ ] Separar credenciais por ambiente com secrets do provedor de CI.
- [ ] Exigir aprovação para produção, se houver produção no escopo.
- [ ] Configurar rollback de deploy ou estratégia segura de rollout.
- [x] Proteger `main` com PR obrigatório e checks requeridos no GitHub.

Critério de aceite: PR inválido não pode ser integrado e commit aprovado gera
imagem rastreável e deploy verificável.

### Marco 10 - Evidências e entrega

- [x] Atualizar README com arquitetura, diagramas e decisões.
- [x] Incluir link do repositório, Swagger/Postman e instruções de execução.
- [ ] Registrar evidência de cobertura >= 80% e quality gate
      (JaCoCo >=80% evidenciado; quality gate Sonar pendente).
- [ ] Registrar evidência do pipeline e deploy
      (pipeline validado no GitHub Actions; deploy Kubernetes não executado).
- [ ] Registrar evidência do dashboard/tracing
      (tracing/dashboard/alertas pendentes; apenas correlationId e métricas).
- [ ] Preparar dados de sandbox reproduzíveis para a demonstração
      (depende das credenciais sandbox da equipe).
- [ ] Ensaiar fluxo feliz completo
      (depende do ambiente e dos serviços do grupo).
- [ ] Ensaiar falha, retry/DLQ e compensação da Saga
      (depende do ambiente e dos serviços do grupo).
- [ ] Entregar ao grupo diagramas, estratégia Saga e justificativas para o PDF.
- [ ] Reservar no roteiro do vídeo a parte do Billing e manter o total em até
      15 minutos.

Critério de aceite: todos os itens exigidos no repositório e no PDF final têm
uma evidência verificável e a demonstração não depende de improvisação.

## 8. Ordem de execução recomendada

1. Fechar contratos e sequência da Saga com o grupo.
2. Criar a fundação Spring Boot e ambiente local.
3. Implementar domínio e persistência de orçamento.
4. Implementar RabbitMQ, idempotência, outbox e eventos.
5. Implementar pagamento e Mercado Pago sandbox.
6. Completar testes, cobertura e BDD.
7. Adicionar observabilidade.
8. Empacotar com Docker e Kubernetes.
9. Automatizar CI/CD e configurar proteção da branch.
10. Consolidar documentação, evidências e roteiro da demonstração.

Os passos 1 a 5 formam o caminho crítico. Kubernetes e pipeline podem começar
em paralelo após a fundação, mas não devem substituir a validação do fluxo de
negócio.

## 9. Riscos principais

- **Contrato indefinido:** causa retrabalho entre Billing, OS e execução.
- **Pagamento mal posicionado:** o grupo precisa decidir em qual etapa ele ocorre.
- **Confusão de Saga:** retries técnicos não substituem compensações de negócio.
- **Webhook inseguro:** confiar apenas no payload permite confirmação indevida.
- **Evento perdido:** persistir e publicar sem outbox abre janela de inconsistência.
- **Duplicidade:** RabbitMQ e webhooks entregam mensagens mais de uma vez.
- **Cobertura tardia:** buscar 80% no fim tende a produzir testes frágeis.
- **Infra não reproduzível:** evidência manual não comprova deploy automatizado.
- **Dependência global:** SQL + NoSQL, BDD, vídeo e observabilidade são requisitos
  do grupo, mas o Billing precisa fornecer sua parte e evidências.

## 10. Definition of Done do Aluno 2

A parte do Aluno 2 estará pronta quando:

- [ ] o Billing executar em repositório, banco e pipeline próprios;
- [ ] orçamento e pagamento funcionarem com regras e estados consistentes;
- [ ] Mercado Pago sandbox estiver integrado de forma segura e idempotente;
- [ ] OS Service receber resultados sem acesso direto ao banco do Billing;
- [ ] a Saga tiver falhas e compensações implementadas e documentadas;
- [ ] os contratos REST e assíncronos estiverem versionados e documentados;
- [ ] testes tiverem cobertura mínima de 80% e quality gate aprovado;
- [ ] o serviço participar do BDD ponta a ponta;
- [ ] Docker, Kubernetes e deploy automatizado estiverem validados;
- [ ] logs, métricas e traces permitirem acompanhar uma OS distribuída;
- [ ] README, Swagger/Postman e evidências estiverem completos;
- [ ] a demonstração feliz e a demonstração de falha estiverem ensaiadas.
