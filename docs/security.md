# Segurança

- CPF é identificador, não segredo; a limitação será registrada na entrega.
- Respostas de autenticação não devem revelar se um CPF está cadastrado.
- CPF deve ser mascarado em logs, traces e alertas.
- JWT terá curta duração e assinatura assimétrica.
- A chave privada ficará fora do código e dos artefatos de build.
- Autorizador e backend validarão assinatura, emissor, audiência e expiração.
- A Lambda terá acesso somente de leitura aos dados necessários do cliente.
- Logs estruturados usarão `requestId`, `trace.id` e `span.id`.

Uma evolução futura recomendada é adicionar segundo fator ou código de uso único.
