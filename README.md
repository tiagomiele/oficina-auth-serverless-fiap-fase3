# Oficina Auth Serverless — Fase 3

Serviço serverless de autenticação por CPF e entrega assíncrona de notificações da oficina mecânica. Implementa AWS Lambda Java 21, JWT RSA, Lambda Authorizer, API Gateway HTTP API, SNS, entrega configurável por log ou SES e infraestrutura Terraform compatível com AWS Academy.

## Responsabilidades

- validar e normalizar CPF;
- consultar a existência e o status do cliente no RDS;
- emitir JWT de curta duração;
- autorizar rotas protegidas no API Gateway;
- produzir logs estruturados em JSON sem CPF ou token;
- expor telemetria opcional no New Relic e log de acesso técnico do API Gateway;
- aceitar notificações técnicas do backend e processá-las de forma assíncrona por SNS, com entrega por log ou SES;
- redirecionar falhas definitivas de entrega para uma DLQ;
- provisionar os componentes serverless com a `LabRole` existente.

Não contém regras de Ordem de Serviço nem infraestrutura do EKS ou do RDS.

## Arquitetura

```mermaid
flowchart LR
    Client[Cliente] --> Gateway[API Gateway]
    Gateway --> Login[Lambda Login CPF]
    Login --> DB[(RDS PostgreSQL)]
    Login --> NR[New Relic]
    Gateway --> Authorizer[Lambda Authorizer]
    Authorizer --> API[Backend no EKS]
    API --> NotificationApi[Lambda Ingresso]
    NotificationApi --> SNS[Amazon SNS]
    SNS --> Delivery[Lambda Entrega]
    Delivery --> Log[Log técnico sem PII]
    Delivery -. modo opcional .-> SES[Amazon SES]
    SNS --> DLQ[SQS DLQ]
```

## Tecnologias

- AWS Lambda, API Gateway, SNS, SQS e SES opcional;
- Java 21;
- JWT com assinatura assimétrica;
- Terraform;
- GitHub Actions;
- New Relic Lambda integration.

## Build e testes

```bash
./mvnw -B verify spotless:check
```

Os testes cobrem login com sucesso, CPF inválido, ausente, inexistente e inativo, JSON inválido, claims do JWT, token ausente, inválido, expirado, com emissor ou audiência incorretos, allow e deny do Authorizer, logs estruturados, encaminhamento de log de acesso e o fluxo assíncrono de notificações sem PII nos logs.

O artefato compartilhado pelas Lambdas é gerado em:

```text
target/oficina-auth.jar
```

## Terraform

O repositório cria a rota pública `POST /auth/cpf`, a rota técnica protegida `POST /internal/notifications` e seis rotas do cliente protegidas pelo Lambda Authorizer. Também cria o tópico SNS, a Lambda de entrega configurável e a DLQ. O modo `log` é o padrão compatível com AWS Academy; o modo `ses` exige uma identidade previamente verificada e permissões que a `LabRole` pode bloquear. Configure um workspace HCP por ambiente, execute o build antes do plan e nunca versione chaves ou credenciais.

```bash
./mvnw -B -DskipTests package
terraform init -input=false -lockfile=readonly
terraform plan -input=false -no-color
```

O CI apresenta quatro jobs sequenciais: `Repository validation → Java build and tests → Terraform validation → Security validation`. Pull Requests para `homolog` ou `main` executam plan sem apply. Após o merge, o próprio deploy executa `plan → apply → sincronização das URLs do API Gateway com o Backend`. Em `homolog`, o fluxo aparece como `Validate configuration and AWS → Package Lambda artifact → Terraform Auth → Deployment summary`; em `main`, toda a execução permanece em um único job protegido para exigir somente uma aprovação do GitHub Environment `production`. O `workflow_dispatch` permite repetir o deploy a partir da branch correspondente para bootstrap ou recuperação. Destroy permanece manual via Terraform CLI e não faz parte da esteira. Configuração ausente ou sessão AWS inválida falha explicitamente. Consulte [AWS Academy e deploy](docs/deployment.md).

Após o apply, `scripts/sync-auth-outputs.py` atualiza `API_GATEWAY_BASE_URL`, `AUTH_BASE_URL` e `NOTIFICATION_ENDPOINT` no Backend. A escrita usa preferencialmente uma GitHub App instalada somente em `oficina-backend-fiap-fase3`, com permissão **Environments: read and write**; configure `SYNC_APP_CLIENT_ID` com o Client ID da GitHub App e `SYNC_APP_PRIVATE_KEY` com a chave privada, uma única vez no repositório Auth. `GITHUB_SYNC_TOKEN` permanece somente como alternativa temporária de recuperação.

## Observabilidade

A instrumentação New Relic e o encaminhamento do log de acesso são opcionais e desligados por padrão. Variáveis, ARNs de camada e consultas estão em [Observabilidade](docs/observability.md).

## Contrato

O contrato de autenticação, rotas protegidas e ingresso técnico de notificações está em [`docs/openapi/oficina-auth.yaml`](docs/openapi/oficina-auth.yaml) e pode ser importado diretamente na collection central do Postman.

## Documentação

- [Arquitetura e contratos](docs/architecture.md)
- [Observabilidade](docs/observability.md)
- [Contrato OpenAPI](docs/openapi/oficina-auth.yaml)
- [Segurança](docs/security.md)
- [AWS Academy e deploy](docs/deployment.md)
- [Repositórios da solução](docs/repositories.md)

## Contribuição

- mudanças somente por Pull Request;
- `main` representa produção;
- `homolog` representa homologação;
- CI aprovado antes do merge;
- nenhum CPF, token ou segredo em logs e arquivos versionados.
