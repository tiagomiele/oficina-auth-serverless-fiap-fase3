# Oficina Auth Serverless — Fase 3

Serviço serverless de autenticação por CPF da oficina mecânica. Implementa AWS Lambda Java 21, JWT RSA, Lambda Authorizer, API Gateway HTTP API e infraestrutura Terraform compatível com AWS Academy.

## Responsabilidades

- validar e normalizar CPF;
- consultar a existência e o status do cliente no RDS;
- emitir JWT de curta duração;
- autorizar rotas protegidas no API Gateway;
- produzir logs estruturados em JSON sem CPF ou token;
- expor telemetria opcional no New Relic e log de acesso técnico do API Gateway;
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
```

## Tecnologias

- AWS Lambda e API Gateway;
- Java 21;
- JWT com assinatura assimétrica;
- Terraform;
- GitHub Actions;
- New Relic Lambda integration.

## Build e testes

```bash
./mvnw -B verify spotless:check
```

Os testes cobrem login com sucesso, CPF inválido, ausente, inexistente e inativo, JSON inválido, claims do JWT, token ausente, inválido, expirado, com emissor ou audiência incorretos, allow e deny do Authorizer, logs estruturados e o encaminhamento de log de acesso.

O artefato usado pelas duas Lambdas é gerado em:

```text
target/oficina-auth.jar
```

## Terraform

O repositório cria a rota pública `POST /auth/cpf` e seis rotas do cliente protegidas pelo Lambda Authorizer. Configure um workspace HCP por ambiente, execute o build antes do plan e nunca versione chaves ou credenciais.

```bash
./mvnw -B -DskipTests package
terraform init -input=false
terraform plan -input=false -no-color
```

O `apply` permanece manual e exige `TF_APPLY_ENABLED=true` no GitHub Environment mais aprovação dos revisores. Consulte [AWS Academy e deploy](docs/deployment.md) para as variáveis, os comandos exatos e a ordem segura de implantação.

## Observabilidade

A instrumentação New Relic e o encaminhamento do log de acesso são opcionais e desligados por padrão. Variáveis, ARNs de camada e consultas estão em [Observabilidade](docs/observability.md).

## Contrato

O contrato de `POST /auth/cpf` e das rotas protegidas está em [`docs/openapi/oficina-auth.yaml`](docs/openapi/oficina-auth.yaml) e pode ser importado diretamente na collection central do Postman.

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
