# CI/CD do Billing Service

Este documento descreve os workflows de integração e entrega contínua do
Billing Service e o que ainda precisa ser provisionado no repositório para que
o pipeline fique completo.

## Workflows

### Billing CI (`.github/workflows/ci.yml`)

Dispara em push para qualquer branch, em pull requests e manualmente
(`workflow_dispatch`). Jobs:

- **Build and tests** (`verify`): checkout, JDK 17 (Temurin), verificação do
  Docker e `./mvnw clean verify`. Os testes de integração com Testcontainers
  são obrigatórios — não são pulados silenciosamente. Ao final, os relatórios
  de teste (`target/surefire-reports/`) e de cobertura JaCoCo
  (`target/site/jacoco/`) são publicados como artefato `test-evidence`.
- **Kubernetes manifests** (`manifests`): valida os arquivos de `k8s/` com
  kubeconform em modo estrito.
- **Container build** (`image`): constrói a imagem Docker com Buildx para
  conferir o Dockerfile. Não publica em registry.
- **Sonar quality gate** (`sonar`): opcional, só executa quando a variável de
  repositório `SONAR_ENABLED` é `true` e o evento não é um pull request de
  fork. Compila o código, baixa o artefato `test-evidence` e roda
  `./mvnw sonar:sonar` aguardando o quality gate.

Os checks obrigatórios na proteção de branch devem ser os contextos
`Build and tests`, `Kubernetes manifests` e `Container build`.

### Billing Release (`.github/workflows/release.yml`)

Dispara em push para `main` e manualmente (somente `main`; o job falha
explicitamente se disparado em outra ref). Jobs:

- **Validate and quality gate** (`validate`): repete `./mvnw clean verify`
  completo e exige o quality gate do Sonar — se `SONAR_ENABLED` não for
  `true`, o job falha com mensagem explicativa. Sonar é **obrigatório** para
  release.
- **Publish image to GHCR** (`publish`): login no GHCR com `GITHUB_TOKEN`,
  build e push da imagem `ghcr.io/<owner>/<repo>:sha-<sha>`. A saída do job é
  a referência imutável por digest
  `ghcr.io/<owner>/<repo>@sha256:<digest>`.
- **Deploy to staging** (`deploy`): opcional, só executa quando
  `KUBERNETES_DEPLOY_ENABLED` é `true` e a ref é `main`. Usa o ambiente
  `staging`, decodifica o kubeconfig do secret `KUBE_CONFIG_BASE64` em arquivo
  temporário (permissão 600, removido ao final), aplica `configmap` e
  `service`, confirma que o Secret `billing-service-secrets` já
  existe no cluster (não cria nem altera), renderiza o `deployment.yaml`
  localmente com a imagem por digest via `kubectl set image --local`, aplica e
  aguarda `rollout status` por até 180s.

## Variáveis e secrets do repositório

Apenas os nomes são documentados; os valores ficam no GitHub.

### Repository variables

- `SONAR_ENABLED` — `true` habilita o job de Sonar no CI e é exigido no
  release.
- `SONAR_PROJECT_KEY` — chave do projeto no servidor Sonar.
- `SONAR_HOST_URL` — URL do servidor Sonar (ex.: `https://sonarcloud.io`).
- `SONAR_ORGANIZATION` — opcional; organização no SonarCloud.
- `KUBERNETES_DEPLOY_ENABLED` — `true` habilita o job de deploy.

### Repository secrets

- `SONAR_TOKEN` — token de acesso ao servidor Sonar.
- `GITHUB_TOKEN` — fornecido automaticamente; usado para publicar no GHCR.

### Environment `staging`

- Secret `KUBE_CONFIG_BASE64` — kubeconfig do cluster codificado em base64,
  com o menor escopo possível (recomenda-se uma service account restrita ao
  namespace `mecanica`).

## Pré-requisitos externos

Antes de habilitar o deploy:

1. Criar o namespace `mecanica` (responsabilidade do dono da infraestrutura).
   O pipeline aplica **somente recursos namespaced** e não cria o namespace —
   a credencial de deploy pode ter escopo restrito ao namespace.
2. Criar o Secret da aplicação no cluster (ver `k8s/secret.yaml`):
   `kubectl create secret generic billing-service-secrets -n mecanica ...`.
   O pipeline **não** aplica o arquivo de placeholders.
3. Garantir MongoDB acessível pelo cluster em modo replica set (o
   `MONGODB_URI` exige `replicaSet` para transações).
4. Garantir o RabbitMQ acessível pelo cluster com o exchange e a DLX
   configurados.
5. Permitir que o kubelet faça pull da imagem no GHCR: tornar o pacote
   público ou criar um `imagePullSecret` no namespace `mecanica`.

## Rollback

O pipeline não executa rollback automático (não há revisão anterior garantida
para o primeiro deploy). Para reverter manualmente:

```bash
kubectl -n mecanica rollout undo deployment/billing-service
```

## Estado atual e evidências pendentes

- Workflows de CI e release **criados** neste repositório.
- [Primeira execução do Billing CI aprovada](https://github.com/alexandrasoares/mecanica-billing-service/actions/runs/43959216320),
  no commit `1b90fff3090b6fd649df7fa53cbdd248bddc2ad3`: 68 testes sem falhas
  ou testes ignorados, gate JaCoCo aprovado, cinco manifests válidos e imagem
  Docker construída. Os relatórios estão no artefato `test-evidence` dessa
  execução. O job Sonar foi ignorado por falta de configuração.
- Proteção da branch `main` **configurada**: exige PR com 1 aprovação,
  descarta aprovações obsoletas, exige resolução de conversas, exige branch
  atualizada com os checks `Build and tests`, `Kubernetes manifests` e
  `Container build`, aplica-se a administradores e bloqueia force-push e
  exclusão da branch.
- Pendente: análise Sonar e quality gate, configuração das variáveis e
  secrets acima, publicação no GHCR, provisionamento do ambiente `staging`
  e deploy real validado.
- Scans de dependências, secrets e imagem ainda **não** foram implementados.
- A Etapa 10 (CI/CD e proteção de branch) não está concluída enquanto esses
  itens externos não forem verificados.
