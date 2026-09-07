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
| `homolog-plan` | Plan sem apply para Pull Requests destinados a `homolog` |
| `production-plan` | Plan sem apply para Pull Requests destinados a `main` |
| `homolog` | Apply automático após merge em `homolog`, sem aprovação manual |
| `production` | Apply após merge em `main`, com uma única aprovação manual |

Credenciais AWS, token HCP e variables dos environments são gerenciados por `scripts/configure-environment.ps1` no repositório do Backend. A autorização de sincronização usa preferencialmente `SYNC_APP_CLIENT_ID` e `SYNC_APP_PRIVATE_KEY`, configurados uma única vez no nível do repositório Auth.

Pull Requests destinados a `homolog` ou `main` executam um plan sem apply nos environments de plan. O workflow separado não exige execução manual. Após o merge, o deploy executa plan, apply, captura os outputs e sincroniza `API_GATEWAY_BASE_URL`, `AUTH_BASE_URL` e `NOTIFICATION_ENDPOINT` com o Backend no mesmo run. Em `homolog`, o workflow exibe quatro jobs sequenciais: validação da configuração e da sessão AWS → empacotamento da Lambda → plan/apply/sincronização → resumo. Merges em `main` usam um único job no environment `production`, que concentra a única aprovação humana antes de toda a execução. Configuração ausente ou credencial expirada falha explicitamente, sem falso sucesso.

A GitHub App deve estar instalada somente em `oficina-backend-fiap-fase3`, com permissão **Environments: read and write**. Configure o Client ID da GitHub App como variable `SYNC_APP_CLIENT_ID` e a chave privada como secret `SYNC_APP_PRIVATE_KEY` no nível do repositório Auth. O secret `GITHUB_SYNC_TOKEN` é aceito apenas como alternativa temporária de recuperação.

O `workflow_dispatch` do deploy permite repetir o fluxo completo para bootstrap ou recuperação: selecione a branch `homolog` para homologação ou `main` para produção. O workflow recusa outras branches. Destroy não faz parte da esteira e deve ser executado manualmente com Terraform CLI, sem `-auto-approve`.

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

O script configura `environment`, rede, banco, chaves JWT, URL do backend, chave técnica de notificação e remetente SES. Região, issuer, audience e TTL usam defaults. A `LabRole` é derivada automaticamente da conta autenticada. Use `-ConfigureNewRelic` para as variáveis de observabilidade. Após o apply, a sincronização com o Backend é automática e não exige reexecutar esse script.

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

Apply manual de recuperação:

```bash
terraform apply -input=false -no-color
```

Destroy operacional, fora da esteira e com confirmação interativa:

```bash
terraform destroy -input=false -no-color
```

## Troubleshooting

`ExpiredToken`, `RequestExpired` ou `InvalidClientTokenId` no plan indicam sessão do Learner Lab expirada. O laboratório encerra a sessão periodicamente e as credenciais são temporárias.

1. reinicie o laboratório e copie o bloco `[default]` atual em **AWS Details**;
2. execute uma vez `configure-environment.ps1` no backend;
3. confirme com `./scripts/validate-aws-session.sh`, que falha explicitamente quando falta credencial ou o token expirou;
4. reexecute o workflow.

Em execução manual ou automática, credencial ausente ou expirada interrompe o job antes de qualquer chamada Terraform.

## Ordem segura

1. aplicar Kubernetes e RDS na ordem do bootstrap documentado;
2. executar a migration do backend;
3. gerar `target/oficina-auth.jar`;
4. revisar o plan do Pull Request;
5. no AWS Academy, confirmar o modo `log`; em conta com SES, confirmar previamente a identidade do remetente;
6. fazer merge em `homolog` para plan, apply e sincronização automáticos;
7. após o primeiro deploy do Backend, configurar `backend_base_url` e repetir o deploy do Auth para publicar as rotas protegidas com o LoadBalancer real.

A Lambda reutiliza o security group do EKS já permitido no RDS. Nenhuma role IAM ou EKS Access Entry é criada.
