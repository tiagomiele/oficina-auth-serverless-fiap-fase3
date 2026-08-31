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
| `homolog` | Plan e apply de homologação a partir de `homolog` |
| `production` | Plan e apply de produção a partir de `main` |

Secrets e variables dos environments são gerenciados por `scripts/configure-environment.ps1` no repositório do backend. Não os copie manualmente.

Merges em `homolog` ou `main` empacotam o código, validam a sessão, executam plan e iniciam o apply. Configuração ausente ou credencial expirada falha explicitamente, sem falso sucesso. O apply aguarda a aprovação do GitHub Environment e exige `TF_APPLY_ENABLED=true`.

`workflow_dispatch` permanece para reexecução: `apply_enabled=false` executa somente o plan e `true` também solicita o gate de apply.

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

No AWS Academy, a identidade do remetente usa a API clássica `VerifyEmailIdentity`, pois a `LabRole` bloqueia `CreateEmailIdentity` e `TagResource` da API SESv2. O apply solicita a verificação sem tags; confirme o e-mail enviado pela AWS antes do teste de entrega.

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
6. confirmar a identidade do remetente enviada pelo SES, sem expor a chave técnica;
7. executar apply somente com autorização explícita;
8. configurar `backend_base_url` e executar novo plan para publicar as rotas protegidas;
9. sincronizar o output `notification_endpoint` com o GitHub Environment do backend.

A Lambda reutiliza o security group do EKS já permitido no RDS. Nenhuma role IAM ou EKS Access Entry é criada.
