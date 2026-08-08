# AWS Academy e deploy

O deploy utilizará AWS Academy Learner Lab e deverá reutilizar a `LabRole`. A pipeline validará a sessão temporária antes de executar Terraform ou publicar a Lambda.

## Ambientes

| Branch | Ambiente |
|---|---|
| `homolog` | Homologação |
| `main` | Produção |

Credenciais temporárias serão configuradas em GitHub Environments e renovadas quando o laboratório reiniciar. Nenhuma credencial será versionada.

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
- `jwt_public_key` em PEM, que também deve ser configurada no backend.

## Ordem segura

1. aplicar Kubernetes somente após aprovação;
2. aplicar RDS e executar a migration do backend;
3. gerar `target/oficina-auth.jar`;
4. executar o plan do workspace de autenticação;
5. revisar recursos, rotas e variáveis;
6. executar apply somente com autorização explícita;
7. configurar `backend_base_url` e executar novo plan para publicar as rotas protegidas.

A Lambda reutiliza o security group do EKS já permitido no RDS. Nenhuma role IAM ou EKS Access Entry é criada.
