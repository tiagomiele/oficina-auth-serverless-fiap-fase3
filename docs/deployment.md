# AWS Academy e deploy

O deploy utilizará AWS Academy Learner Lab e deverá reutilizar a `LabRole`. A pipeline validará a sessão temporária antes de executar Terraform ou publicar a Lambda.

## Ambientes

| Branch | Ambiente |
|---|---|
| `homolog` | Homologação |
| `main` | Produção |

Credenciais temporárias são configuradas em GitHub Environments e renovadas quando o laboratório reinicia. Nenhuma credencial é versionada.

GitHub Environments usados pelos workflows:

| Environment | Uso |
|---|---|
| `homolog` | Plan de homologação a partir de `homolog` |
| `production` | Plan de produção a partir de `main` |
| `homolog-apply` | Aprovação manual do apply de homologação |
| `production-apply` | Aprovação manual e explícita do apply de produção |

Secrets por environment: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `TF_API_TOKEN`.

Variables por environment: `TF_CLOUD_ORGANIZATION`, `TF_WORKSPACE_HOMOLOG`, `TF_WORKSPACE_PRODUCTION`, `AWS_REGION` e `TF_APPLY_ENABLED`.

O `apply` só executa quando o disparo é manual com `apply_enabled=true`, o environment de apply aprova a execução e `TF_APPLY_ENABLED` vale `true`. Qualquer condição ausente falha o job.

## Workspaces HCP Terraform

Crie workspaces de execução remota e apply manual:

```text
oficina-auth-homolog
oficina-auth-production
```

Variáveis não sensíveis:

- `aws_region=us-west-2`;
- `environment`;
- `lab_role_arn` no formato `arn:aws:iam::<conta>:role/LabRole`;
- `private_subnet_ids` e `lambda_security_group_id` obtidos do workspace Kubernetes;
- `jwt_issuer=oficina-auth-serverless`;
- `jwt_audience=oficina-backend`;
- `jwt_ttl_seconds=900`;
- `backend_base_url`, quando o LoadBalancer do backend existir.

Variáveis sensíveis:

- `db_url`, `db_user` e `db_password`;
- `jwt_private_key` em PKCS#8 PEM;
- `jwt_public_key` em PEM, que também deve ser configurada no backend;
- `newrelic_license_key`, quando a instrumentação estiver habilitada.

As variáveis de observabilidade estão documentadas em [Observabilidade](observability.md).

## Comandos exatos

Validação local sem custo e sem credenciais remotas:

```bash
./mvnw -B verify spotless:check
terraform fmt -check -recursive
terraform init -backend=false -input=false
terraform validate -no-color
tflint --recursive
```

Plan com credenciais do laboratório:

```bash
export TF_CLOUD_ORGANIZATION=<organizacao>
export TF_WORKSPACE=oficina-auth-homolog   # ou oficina-auth-production
./scripts/validate-aws-session.sh
./mvnw -B -DskipTests package
terraform init -input=false
terraform plan -input=false -no-color
```

Apply, somente após aprovação explícita:

```bash
terraform apply -input=false -no-color
```

## Troubleshooting

`ExpiredToken`, `RequestExpired` ou `InvalidClientTokenId` no plan indicam sessão do Learner Lab expirada. O laboratório encerra a sessão periodicamente e as credenciais são temporárias.

1. reinicie o laboratório e copie as três credenciais atuais em **AWS Details**;
2. atualize `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` no GitHub Environment e no workspace HCP;
3. confirme com `./scripts/validate-aws-session.sh`, que falha explicitamente quando falta credencial ou o token expirou;
4. reexecute o workflow.

O script nunca é silenciosamente ignorado: credencial ausente ou expirada interrompe o job antes de qualquer chamada Terraform.

## Ordem segura

1. aplicar Kubernetes somente após aprovação;
2. aplicar RDS e executar a migration do backend;
3. gerar `target/oficina-auth.jar`;
4. executar o plan do workspace de autenticação;
5. revisar recursos, rotas e variáveis;
6. executar apply somente com autorização explícita;
7. configurar `backend_base_url` e executar novo plan para publicar as rotas protegidas.

A Lambda reutiliza o security group do EKS já permitido no RDS. Nenhuma role IAM ou EKS Access Entry é criada.
