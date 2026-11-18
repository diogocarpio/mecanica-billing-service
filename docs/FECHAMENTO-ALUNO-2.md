# Fechamento — somente Aluno 2 / Billing

## Fontes e limites

Revisão de 18/11/2026 baseada em:

- `SOAT - Fase 4 - Tech challenge.pdf`, páginas 2–6, nos anexos locais.
- Roteiro original fornecido pelo usuário: “TECH CHALLENGE — FASE 4 —
  ALUNO 2 — MICROSSERVIÇO BILLING SERVICE”, recuperado do histórico.
- Captura do fórum fornecida pelo usuário: orientação encaminhada em
  “Dúvidas sobre o PDF”, de 03/11/2026, permitindo serviço simples de
  pagamento com token próprio. Guardar essa captura junto à entrega.

O PDF exige Mercado Pago; a alternativa simulada se apoia na orientação do
fórum. O Billing usa Bearer próprio do simulador, separado da autenticação
da aplicação. O token de demonstração é configurado previamente; não existe
um fluxo de emissão/login do provedor. Não apresentar como sandbox real.

O Aluno 2 entrega o Billing e suas evidências. Não implementa abertura ou
histórico da OS, fila de execução/reparo, banco SQL de outro serviço nem
infraestrutura completa dos colegas. A atualização da OS depende do evento
do Billing ser consumido pelo OS Service. Essa fronteira precisa ser
demonstrada com o grupo.

## Percentual auditável

**Estimativa de entrega: 67% (8 / 12 critérios).** Não é previsão de nota.
Critérios com peso igual: concluído = 1; parcial = 0,5; sem evidência = 0.
Considera implementação e evidências disponíveis, inclusive materiais ainda
locais. A publicação final e o vídeo permanecem pendentes.

| Critério exigido no recorte do Billing | Estado e evidência | Pontos |
| --- | --- | --- |
| Serviço, repositório e banco próprios; isolamento de dados | Spring Boot + MongoDB próprio + Compose; arquitetura documentada | 1 |
| Gerar e enviar orçamento para aprovação | API de criação, submit, aprovação/rejeição; fluxo local executado | 1 |
| Registrar/verificar pagamento e integrar provedor | Adapter + simulador autenticado; aprovação, recusa e reenvio testados localmente | 1 |
| Comunicar pagamento para atualizar a OS | Eventos implementados; consumo e mudança real na OS ainda não comprovados | 0,5 |
| Saga com falhas e compensação | Coreografia justificada; cancelamento e testes locais; falta prova distribuída | 0,5 |
| Testes unitários e cobertura mínima de 80% | Relatórios locais: 77 testes, zero falhas/erros/skips; JaCoCo 985/1067 linhas = 92,31% | 1 |
| Participar de um fluxo BDD completo entre serviços | Cucumber local disponível; falta execução envolvendo OS e demais serviços | 0,5 |
| Qualidade via SonarQube ou similar no CI | Scanner preparado; última evidência disponível indica Sonar ignorado/bloqueado | 0 |
| CI/CD próprio, proteção da main, Docker/Kubernetes e deploy automático | CI/build e manifests evidenciados; release/deploy Kubernetes não comprovados | 0,5 |
| Usar observabilidade da Fase 3 no fluxo distribuído | Métricas e correlação locais; falta integração e rastreamento entre serviços | 0,5 |
| Arquitetura, execução e Swagger/Postman documentados | README, ADR, Saga, AsyncAPI e OpenAPI disponíveis | 1 |
| Contribuição do Billing ao vídeo/PDF | Dados e roteiro preparados; gravação, link e repasse ao grupo pendentes | 0,5 |

Os XMLs existentes foram inspecionados nesta revisão; a suíte não foi
reexecutada. O percentual de linhas acima corrige a aproximação anterior de
92,33%. O fluxo do simulador validou o estado no Billing, mas não comprovou
consumo pelo OS Service nem contagem de eventos publicados na Outbox.
As evidências remotas citadas em `ENTREGAS-EVIDENCIAS.md` são históricas;
o estado remoto não foi consultado novamente nesta revisão.

## Checklist final por prioridade

### P0 — preparar a entrega e a gravação

- [ ] Publicar a versão final do Billing: revisar diff, commit/push e guardar
  link do commit e execução de CI correspondente.
- [ ] Ensaiar a demonstração do Billing com um orçamento novo: aprovação →
  pagamento → webhook → confirmação; incluir recusa e notificação repetida.
- [ ] Comprovar que a aprovação gera um único evento na Outbox após reenvio
  e que ele foi publicado no broker; não confundir estado final com prova
  de entrega do evento.
- [ ] Gravar o trecho do Aluno 2 (aproximadamente 4 minutos), usando o roteiro
  abaixo e deixando explícito que o provedor é simulado.
- [ ] Repassar ao responsável pela entrega: gravação, nome/RM, URL do repo,
  commit, evidências, arquitetura/Saga e orientação do fórum. Confirmar que
  o trecho está no vídeo final e o repositório no PDF.

### P1 — exigências ainda abertas; resolver com o ambiente do grupo

- [ ] Executar análise Sonar ou equivalente no CI e guardar resultado.
  SonarCloud especificamente não é obrigatório. Um scanner configurado
  ou um job ignorado não comprova análise executada.
- [ ] Executar deploy automático do Billing em Kubernetes; guardar workflow,
  imagem e rollout/health. Usar o cluster e registry existentes do grupo.
- [ ] Executar o trecho Billing no BDD entre serviços, confirmando consumo do
  evento e status da OS, incluindo falha/compensação.
- [ ] Mostrar Billing nas ferramentas da Fase 3 e uma correlação atravessando
  serviços. Endpoint Prometheus isolado não comprova integração.

Essas exigências continuam abertas mesmo se não houver acesso a tempo.
O vídeo do grupo exige demonstrar deploy de **pelo menos um** serviço,
mas o texto também exige pipeline e deploy automatizado **por serviço**.
O deploy de um colega resolve o trecho do vídeo, não a lacuna do Billing.

### Itens sem exigência explícita adicional para este fechamento

Não priorizar contratação de sandbox real, migração de registry para GHCR,
Fortify/Trivy como produtos específicos, novo cluster via Terraform, novo
frontend, e-mail, estorno financeiro adicional ou dashboards/alertas novos.
O obrigatório é demonstrar pagamento conforme a alternativa do fórum,
análise de qualidade no CI, deploy Kubernetes, compensação da Saga e uso
das ferramentas de observabilidade existentes. Não retirar essas exigências.

## Distribuição do tempo restante

Reservar pelo menos 25 minutos para gravação, revisão e repasse:

1. Primeiros 5 minutos: obter do grupo acesso/link de qualidade, cluster,
   BDD e observabilidade já disponíveis; fechar o que for imediatamente
   executável e identificar o que permanece sem evidência.
2. Próximos 15 minutos: publicar versão final, guardar CI/cobertura e ensaiar
   aprovação, recusa, repetição e evento. Reusar ambiente existente.
3. Próximos 15 minutos: gravar e revisar o trecho do Billing.
4. Próximos 10 minutos: repassar material e confirmar inclusão no PDF/vídeo.
5. Saldo: corrigir apenas falha que impeça a execução ou a entrega.

Se houver menos de 45 minutos, reduzir investigação de infraestrutura nova
e preservar a janela de gravação. Documentar lacunas em vez de declarar
validação inexistente.

## Roteiro falado — aproximadamente 4 minutos

Antes de gravar: subir dependências e Billing conforme
`integrations/MERCADO-PAGO.md`; abrir Swagger, checkout, relatório JaCoCo,
CI e diagrama da Saga. Usar IDs novos e validade futura no orçamento.
Não mostrar variáveis, tokens, assinatura ou URL completa com token.

| Tempo | Mostrar | Fala sugerida |
| --- | --- | --- |
| 0:00–0:25 | README e diagrama do Billing | “Sou responsável pelo Billing, que gera orçamentos e registra e verifica pagamentos. O serviço tem repositório e banco MongoDB próprios e se comunica por eventos no RabbitMQ. A OS é atualizada pelo serviço responsável por ela.” |
| 0:25–1:05 | Swagger: criar, enviar e aprovar orçamento | “O total é calculado pelos itens. Envio o orçamento para aprovação e registro a decisão. Somente depois da aprovação é possível criar o pagamento.” |
| 1:05–1:55 | Criar pagamento, abrir checkout e aprovar; consultar pagamento | “Aqui usamos um provedor simulado, conforme a orientação do fórum. Ele exige um token próprio. Ao aprovar, envia um webhook assinado. O Billing valida a assinatura, consulta o provedor e confere referência, valor e moeda antes de registrar APPROVED.” |
| 1:55–2:20 | Reenviar notificação; consultar pagamento e evento | “Uma repetição não deve criar outro pagamento nem outro efeito financeiro.” Mostrar o evento único apenas depois de conferir Outbox/broker; se não conferido, mostrar somente que o estado permanece aprovado. |
| 2:20–2:55 | Recusa em outro pagamento; cenário de cancelamento da Saga | “Uma recusa é registrada como REJECTED. A compensação de orçamento reage ao cancelamento da OS e publica seu resultado. Isso é diferente de estorno de um pagamento já aprovado.” Mostrar execução integrada se disponível; caso contrário identificar claramente o cenário como teste local. |
| 2:55–3:25 | JaCoCo e testes/BDD | “Os relatórios disponíveis têm 77 testes aprovados e 92,31% de cobertura de linhas, acima dos 80% exigidos. Temos cenários Cucumber locais de pagamento e falha/compensação.” Mostrar BDD entre serviços somente se executado. |
| 3:25–3:50 | CI, deploy e observabilidade disponíveis | Exibir resultados reais. Se faltar evidência: “O pipeline e os manifests estão preparados; esta execução de deploy/integração ainda não foi validada.” Não chamar build Docker de deploy Kubernetes nem correlationId local de trace distribuído. |
| 3:50–4:00 | Contratos e transição para colega | “O Billing publica o resultado; o OS Service consome o evento e segue o fluxo da ordem.” Passar ao colega para comprovar consumo e mudança de estado, se o ambiente estiver disponível. |

A recusa de pagamento sozinha não demonstra compensação distribuída.
O vídeo final do grupo, de até 15 minutos, deve incluir o fluxo entre
microsserviços, Saga/falha, deploy automatizado com testes e monitoramento.
O trecho isolado do Billing não substitui essas provas.

## Material do Billing para o PDF

- Autor: preencher nome completo e RM do Aluno 2.
- Repositório: https://github.com/alexandrasoares/mecanica-billing-service
- Responsabilidade: geração/envio/decisão de orçamento; registro e verificação
  de pagamento; publicação dos resultados para o OS Service.
- Tecnologias: Java/Spring Boot, MongoDB, RabbitMQ, Docker e Kubernetes.
- Justificativa: orçamento e pagamento formam um domínio separado, com
  persistência própria e comunicação por contratos, sem acesso cruzado a dados.
- Saga: coreografada; cada serviço executa transação local e publica eventos.
  Billing usa Inbox/Outbox e compensa orçamento pendente ao cancelar a OS.
- Diagrama e estratégia: `docs/architecture/SAGA-BILLING.md` e
  `docs/architecture/ADR-001-saga-coreografada.md`.
- Pagamento: adapter compatível com Mercado Pago; demonstração com simulador
  local autenticado e webhook assinado, conforme orientação do fórum.
- Anexar: link do commit/CI, cobertura e vídeo final; registrar quais
  evidências de deploy, BDD e observabilidade foram efetivamente obtidas.
