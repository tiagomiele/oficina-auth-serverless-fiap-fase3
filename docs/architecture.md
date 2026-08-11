# Arquitetura e contratos

## Fluxo de login

1. `POST /auth/cpf` recebe CPF com ou sem máscara.
2. A Lambda normaliza e valida os dígitos verificadores.
3. A Lambda consulta somente os dados necessários do cliente.
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
