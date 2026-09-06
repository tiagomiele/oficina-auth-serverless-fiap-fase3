# Observabilidade

Monitoramento das Lambdas Java 21 ARM64 e do HTTP API. Tudo é parametrizado e desligado por padrão, portanto o comportamento funcional não muda quando a instrumentação está inativa.

## Logs estruturados da aplicação

Cada invocação emite uma única linha JSON com campos exclusivamente técnicos:

```json
{"function":"auth-cpf-login","outcome":"DENIED","requestId":"8f0c1b2d","traceId":"a1b2","spanId":"c3d4","durationMs":42,"errorCode":"CLIENT_NOT_ELIGIBLE"}
```

Regras aplicadas em `br.com.oficina.auth.observability`:

- `StructuredLogger` só serializa a lista fixa de campos acima;
- `LogSanitizer` remove caracteres de controle, trunca em 64 caracteres e substitui valores com formato de documento ou sequências longas de dígitos por `REDACTED`;
- CPF, corpo da requisição, header `Authorization`, JWT, chaves e credenciais de banco nunca são registrados;
- `outcome` assume `SUCCESS`, `DENIED`, `INVALID_REQUEST`, `ERROR` (login), `ALLOW`, `DENY`, `ERROR` (authorizer), `ACCEPTED` (ingresso) e `PROCESSED` (processamento da notificação);
- `errorCode` usa códigos técnicos como `MISSING_CPF`, `INVALID_CPF`, `CLIENT_NOT_ELIGIBLE`, `MALFORMED_JSON`, `TOKEN_MISSING`, `TOKEN_EXPIRED`, `TOKEN_SIGNATURE_INVALID`, `TOKEN_ISSUER_INVALID`, `TOKEN_AUDIENCE_INVALID`, `TOKEN_ROLE_INVALID`, `INTERNAL_ERROR`.

`requestId` é resolvido nesta ordem: header `X-Request-Id` (case-insensitive), `requestContext.requestId` do API Gateway e, por último, o `awsRequestId` da Lambda.

## Instrumentação New Relic

A abordagem suportada atualmente para Java em Lambda é a camada do agente APM (`java-agent-approaches-lambda`), que exige Java 17 ou superior e ativa o modo serverless automaticamente. Usamos a variante slim para ARM64 e o script de inicialização da camada:

- camada padrão: `arn:aws:lambda:<região>:451483290750:layer:NewRelicAgentJavaARM64-slim:<versão>`;
- `AWS_LAMBDA_EXEC_WRAPPER=/opt/newrelic-java-handler` anexa o agente via `JAVA_TOOL_OPTIONS`;
- o handler da Lambda continua sendo o da aplicação, independentemente da instrumentação.

Quando `newrelic_instrumentation_enabled = false`, nenhuma camada é anexada e `Telemetry.fromEnvironment()` devolve a implementação no-op, sem `traceId`/`spanId` no log e sem dependência do agente em runtime.

### Variáveis Terraform

| Variável | Padrão | Uso |
|---|---|---|
| `newrelic_instrumentation_enabled` | `false` | Anexa a camada e as variáveis do agente |
| `newrelic_layer_arn` | `null` | ARN completo; sobrepõe a composição automática |
| `newrelic_layer_account_id` | `451483290750` | Conta publicadora das camadas |
| `newrelic_layer_name` | `NewRelicAgentJavaARM64-slim` | Camada slim ARM64 |
| `newrelic_layer_version` | `1` | Versão publicada na região |
| `newrelic_license_key` | `""` (sensível) | Ingest license key |
| `newrelic_account_id` | `""` | Conta New Relic |
| `newrelic_trusted_account_key` | `""` | Conta pai; vazio reutiliza `newrelic_account_id` |
| `newrelic_distributed_tracing_enabled` | `true` | Distributed tracing |
| `newrelic_log_level` | `warning` | Log do agente |
| `newrelic_send_function_logs` | `false` | Envio de logs da função pela extensão |
| `newrelic_log_forwarding_enabled` | `false` | Cria a Lambda de encaminhamento do log de acesso |
| `newrelic_logs_endpoint` | `https://log-api.newrelic.com/log/v1` | Log API; use o domínio `.eu` para contas europeias |
| `newrelic_access_log_type` | `api-gateway-access` | `logtype` dos registros enviados |
| `api_detailed_metrics_enabled` | `true` | Métricas detalhadas por rota no stage |

`newrelic_license_key` e `newrelic_account_id` são obrigatórias quando a instrumentação está ativa; a validação ocorre no plan por precondition, sem valores no código.

### Configuração da camada

Execute no repositório do backend:

```powershell
.\scripts\configure-environment.ps1 -Environment homolog -ConfigureNewRelic
```

O script descobre automaticamente a versão pública mais recente da camada Java ARM64 na região do laboratório, guarda as chaves fora do Git e sincroniza `newrelic_layer_version`, `newrelic_license_key`, `newrelic_account_id`, instrumentação e forwarding no HCP Terraform. Repita para `production`; depois revise o plan antes de autorizar o apply.

## Monitoramento do API Gateway

O stage `$default` grava log de acesso JSON no log group `/aws/apigateway/<nome>` com `requestId`, `environment`, `apiId`, `stage`, `routeKey`, `httpMethod`, `path`, `protocol`, `status`, `responseLength`, `responseLatency`, `integrationStatus`, `integrationLatency`, `integrationErrorMessage`, `errorMessage` e `authorizerError`. Nenhum header, corpo ou identidade do chamador é registrado.

`default_route_settings.detailed_metrics_enabled` controla as métricas por rota. Cada rota protegida tem sua própria integração HTTP_PROXY, cuja URI repete o caminho da rota (`backend_base_url` + caminho, com as variáveis de caminho preservadas), porque o HTTP API não repassa o caminho automaticamente. Toda integração aplica `append:header.X-Request-Id = $context.requestId`, garantindo correlação no backend mesmo quando o cliente não envia o header. Rotas explícitas, Authorizer e CORS permanecem inalterados.

## Log de acesso no New Relic

Não há integração oficial de logs do CloudWatch para New Relic que dispense a criação de role IAM, o que é incompatível com o AWS Academy. Por isso o repositório traz um encaminhador opcional que reutiliza a `LabRole`:

- `newrelic_log_forwarding_enabled = true` cria a Lambda `<nome>-log-forwarder`, a permissão de invocação para `logs.<região>.amazonaws.com` e o subscription filter no log group do API Gateway;
- a Lambda usa o mesmo artefato Java e o handler `ApiGatewayLogForwarderHandler`;
- `AccessLogSanitizer` mantém apenas a allowlist de campos técnicos do log de acesso e descarta qualquer outro campo, inclusive `authorization`, `body`, `headers` e IP de origem;
- o envio usa HTTPS na Log API com a license key vinda do ambiente, nunca do código;
- desligado por padrão e sem nenhuma role IAM criada.

Consulta no New Relic após habilitar:

```sql
SELECT count(*) FROM Log WHERE logtype = 'api-gateway-access' FACET routeKey, status SINCE 1 hour ago
```

## Validação final com credenciais

Estes passos exigem sessão do Learner Lab e conta New Relic, portanto ficam fora do CI:

1. `terraform plan` com `newrelic_instrumentation_enabled = true` e conferência da camada nas funções instrumentadas;
2. `apply` autorizado e chamada de `POST /auth/cpf`;
3. verificação da entidade das Lambdas em APM no New Relic;
4. `newrelic_log_forwarding_enabled = true` e execução da consulta NRQL acima.
