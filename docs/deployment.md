# AWS Academy e deploy

O deploy utilizará AWS Academy Learner Lab e deverá reutilizar a `LabRole`. A pipeline validará a sessão temporária antes de executar Terraform ou publicar a Lambda.

## Ambientes

| Branch | Ambiente |
|---|---|
| `homolog` | Homologação |
| `main` | Produção |

Credenciais temporárias serão configuradas em GitHub Environments e renovadas quando o laboratório reiniciar. Nenhuma credencial será versionada.

O provisionamento detalhado será implementado na Semana 3.
