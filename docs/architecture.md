# Arquitetura e contratos

## Modelo Clean Architecture

O núcleo Java é independente dos serviços AWS e das bibliotecas de infraestrutura. A direção permitida é:

```text
handler e adapters de saída → application → domain
              infrastructure → composição
```

```text
br.com.oficina.auth
├── domain
│   ├── Cpf
│   ├── AuthorizedClient
│   ├── NotificationMessage
│   └── TokenValidationException
├── application
│   ├── port/in
│   ├── port/out
│   ├── model
│   └── usecase
├── adapter/out
│   ├── jdbc
│   ├── jwt
│   ├── sns
│   ├── ses
│   └── newrelic
├── handler
└── infrastructure/config
```

Os handlers permanecem em `br.com.oficina.auth.handler` porque esses FQCNs são contratos de runtime do Terraform. Eles atuam somente como adaptadores de entrada: interpretam eventos, aplicam autenticação técnica, delegam aos casos de uso e convertem o resultado em resposta Lambda. A classe `AuthComposition` é a composition root responsável por construir os adaptadores concretos e ler variáveis de ambiente.

As classes antigas em `infrastructure`, `notification` e `forwarder` são fachadas de compatibilidade para os testes e contratos já existentes. O caminho de produção é montado com as portas e os adaptadores explícitos.

O teste ArchUnit rejeita dependências de `domain` ou `application` para AWS SDK, Lambda, API Gateway, JDBC, JJWT, Jackson, New Relic, handlers, adapters e infraestrutura. Outro teste protege os construtores públicos e os cinco entrypoints Lambda declarados no Terraform.

## Casos de uso e portas

| Fluxo | Porta de entrada | Caso de uso | Portas de saída |
|---|---|---|---|
| Login por CPF | `AuthenticateClient` | `AuthenticateClientUseCase` | `ClientRepositoryPort`, `TokenIssuerPort` |
| Lambda Authorizer | `AuthorizeToken` | `AuthorizeTokenUseCase` | `TokenValidatorPort` |
| Ingresso de notificação | `PublishNotification` | `PublishNotificationUseCase` | `NotificationPublisherPort` |
| Entrega assíncrona | `DeliverNotification` | `DeliverNotificationUseCase` | `NotificationSenderPort` |
| Logs do API Gateway | `ForwardAccessLogs` | `ForwardAccessLogsUseCase` | `AccessLogPublisherPort` |

## Fluxo de login

1. `POST /auth/cpf` recebe CPF com ou sem máscara.
2. O handler entrega o CPF bruto ao `AuthenticateClientUseCase`.
3. O caso de uso normaliza e valida os dígitos, consulta o cliente ativo pela porta JDBC e solicita o JWT pela porta de emissão.
4. Cliente inválido, inexistente ou inativo recebe resposta genérica `401`.
5. Cliente ativo recebe JWT curto, sem CPF completo no payload.
6. O Lambda Authorizer valida o token das rotas protegidas.

## Claims

- `sub`: identificador técnico do cliente;
- `client_id`: o mesmo identificador numérico do cliente;
- `role`: `CLIENTE`;
- `iss`, `aud`, `iat`, `exp` e `jti`.

## Correlação e observabilidade

O `X-Request-Id` recebido é reutilizado; quando ausente, o API Gateway propaga `$context.requestId` ao backend e as Lambdas devolvem o valor no header da resposta. O contrato completo está em [`openapi/oficina-auth.yaml`](openapi/oficina-auth.yaml) e os detalhes de telemetria em [Observabilidade](observability.md).

## Fluxo de notificação

1. O backend chama `POST /internal/notifications` com `X-Notification-Key` e `X-Request-Id`.
2. O handler compara a chave em tempo constante e delega ao `PublishNotificationUseCase`, que valida os limites do domínio e publica pela porta SNS.
3. O SNS invoca a Lambda de entrega; o `DeliverNotificationUseCase` usa a porta configurada para entrega por log ou Amazon SES.
4. Falhas transitórias são repetidas pelo SNS; falhas definitivas seguem para a fila SQS DLQ.
5. Destinatário, assunto, corpo e credencial não aparecem nos logs técnicos.

O remetente precisa estar verificado no SES. Contas em sandbox também exigem destinatários verificados até a liberação para produção.

## Dependências

- rede e EKS: `oficina-kubernetes-infra-fiap-fase3`;
- RDS: `oficina-database-infra-fiap-fase3`;
- APIs de negócio: `oficina-backend-fiap-fase3`.

## Rotas protegidas

O API Gateway usa o Authorizer somente nas operações do cliente:

- consulta de status e histórico da OS;
- aprovação e rejeição de orçamento;
- confirmação de pagamento.

Rotas administrativas não passam pelo Authorizer de cliente. O backend valida novamente a assinatura RSA, os claims e a propriedade da OS.
