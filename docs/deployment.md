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
| `production` | Plan de produção a partir de `main` |
| `homolog-apply` | Aprovação manual do apply de homologação |
| `production-apply` | Aprovação manual e explícita do apply de produção |

Secrets e variables dos environments são gerenciados por `scripts/configure-environment.ps1` no repositório do backend. Não os copie manualmente.

Em pushes para `homolog` ou `main`, o workflow empacota e valida o código, mas ignora o plan remoto com aviso enquanto AWS ou HCP ainda não estiverem configurados. Depois da execução do script central, o mesmo workflow valida a sessão e executa o plan normalmente. Um disparo manual continua falhando explicitamente quando a configuração estiver incompleta.

O `apply` só executa quando o disparo é manual com `apply_enabled=true`, o environment de apply aprova a execução e `TF_APPLY_ENABLED` vale `true`. Qualquer condição ausente falha o job.

## Workspaces HCP Terraform

Crie workspaces de execução remota e apply manual:

```text
oficina-auth-homolog
oficina-auth-production
```

Execute no repositório do backend:

```powershell
.\scripts\configure-environment.ps1 -Environment homolog
```

O script configura `environment`, rede, banco, chaves JWT e URL do backend. Região, issuer, audience e TTL usam defaults. A `LabRole` é derivada automaticamente da conta autenticada. Use `-ConfigureNewRelic` para as variáveis de observabilidade.

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

Em execução manual, credencial ausente ou expirada interrompe o job antes de qualquer chamada Terraform. Em push automático, configuração ausente gera um aviso e ignora somente o plan remoto; credenciais configuradas, porém expiradas, continuam falhando na validação da sessão.

## Ordem segura

1. aplicar Kubernetes somente após aprovação;
2. aplicar RDS e executar a migration do backend;
3. gerar `target/oficina-auth.jar`;
4. executar o plan do workspace de autenticação;
5. revisar recursos, rotas e variáveis;
6. executar apply somente com autorização explícita;
7. configurar `backend_base_url` e executar novo plan para publicar as rotas protegidas.

A Lambda reutiliza o security group do EKS já permitido no RDS. Nenhuma role IAM ou EKS Access Entry é criada.
