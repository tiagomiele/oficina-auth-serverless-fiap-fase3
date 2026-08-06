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
- `role`: `CLIENTE`;
- `iss`, `aud`, `iat`, `exp` e `jti`.

## Dependências

- rede e EKS: `oficina-kubernetes-infra-fiap-fase3`;
- RDS: `oficina-database-infra-fiap-fase3`;
- APIs de negócio: `oficina-backend-fiap-fase3`.

O contrato OpenAPI será versionado antes da implementação da Lambda.
