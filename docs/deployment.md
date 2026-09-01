# AWS Academy e deploy

O deploy utilizará AWS Academy Learner Lab e deverá reutilizar a `LabRole`. A pipeline validará a sessão temporária antes de executar Terraform ou publicar a Lambda.

## Ambientes

| Branch | Ambiente |
|---|---|
| `homolog` | Homologação |
| `main` | Produção |

Credenciais temporárias são renovadas uma única vez pelo script central do backend e propagadas aos GitHub Environments e ao Variable Set HCP. Nenhuma credencial é versionada.

GitHub Environments usados pelos workflows:

| Environment | Uso |
|---|---|
| `homolog` | Plan de homologação a partir de `homolog` |
| `homolog-apply` | Apply e destroy de homologação |
| `production` | Plan de produção a partir de `main` |
| `production-apply` | Apply e destroy de produção |

Secrets e variables dos environments são gerenciados por `scripts/configure-environment.ps1` no repositório do backend. Não os copie manualmente.

Merges em `homolog` empacotam o código, validam a sessão, executam plan e iniciam o apply. Configuração ausente ou credencial expirada falha explicitamente, sem falso sucesso. O plan usa o environment `homolog`; apply e destroy usam `homolog-apply` ou `production-apply`, aguardam aprovação e exigem `TF_APPLY_ENABLED=true`.

Como o workflow está presente em `main`, `workflow_dispatch` oferece as operações `plan`, `apply` e `destroy`. Selecione a própria branch `homolog` para homologação ou `main` para produção. Apply e destroy exigem a confirmação textual exata `APPLY-<ambiente>` ou `DESTROY-<ambiente>`; produção não pode ser executada a partir de outra branch.

## Workspaces HCP Terraform

Crie workspaces de execução remota com Auto apply desativado:

```text
oficina-auth-homolog
oficina-auth-production
```

Execute no repositório do backend:

```powershell
.\scripts\configure-environment.ps1 -Environment homolog
```

O script configura `environment`, rede, banco, chaves JWT, URL do backend, chave técnica de notificação e remetente SES. Região, issuer, audience e TTL usam defaults. A `LabRole` é derivada automaticamente da conta autenticada. Use `-ConfigureNewRelic` para as variáveis de observabilidade.

No AWS Academy, use `notification_delivery_mode = "log"` e `notification_create_ses_identity = false`: a `LabRole` bloqueia `CreateEmailIdentity`, `TagResource` e `VerifyEmailIdentity`. O fluxo continua assíncrono por API Gateway, SNS e Lambda, mas registra somente o resultado técnico sem dados pessoais. Em uma conta AWS com permissão SES, use o modo `ses` e habilite opcionalmente a solicitação de verificação do remetente.

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
terraform init -input=false -lockfile=readonly
terraform plan -input=false -no-color
```

Apply, somente após aprovação explícita:

```bash
terraform apply -input=false -no-color
```

## Troubleshooting

`ExpiredToken`, `RequestExpired` ou `InvalidClientTokenId` no plan indicam sessão do Learner Lab expirada. O laboratório encerra a sessão periodicamente e as credenciais são temporárias.

1. reinicie o laboratório e copie o bloco `[default]` atual em **AWS Details**;
2. execute uma vez `configure-environment.ps1` no backend;
3. confirme com `./scripts/validate-aws-session.sh`, que falha explicitamente quando falta credencial ou o token expirou;
4. reexecute o workflow.

Em execução manual ou automática, credencial ausente ou expirada interrompe o job antes de qualquer chamada Terraform.

## Ordem segura

1. aplicar Kubernetes somente após aprovação;
2. aplicar RDS e executar a migration do backend;
3. gerar `target/oficina-auth.jar`;
4. executar o plan do workspace de autenticação;
5. revisar recursos, rotas, tópico SNS, Lambda de entrega, DLQ e variáveis;
6. no AWS Academy, confirmar o modo `log`; em conta com SES, confirmar previamente a identidade do remetente;
7. executar apply somente com autorização explícita;
8. configurar `backend_base_url` e executar novo plan para publicar as rotas protegidas;
9. sincronizar o output `notification_endpoint` com o GitHub Environment do backend.

A Lambda reutiliza o security group do EKS já permitido no RDS. Nenhuma role IAM ou EKS Access Entry é criada.
