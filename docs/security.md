# Segurança

- CPF é identificador, não segredo; a limitação será registrada na entrega.
- Respostas de autenticação não devem revelar se um CPF está cadastrado.
- CPF deve ser mascarado em logs, traces e alertas.
- JWT terá curta duração e assinatura assimétrica.
- A chave privada ficará fora do código e dos artefatos de build.
- Autorizador e backend validarão assinatura, emissor, audiência e expiração.
- A Lambda terá acesso somente de leitura aos dados necessários do cliente.
- Logs estruturados usam apenas `function`, `outcome`, `requestId`, `traceId`, `spanId`, `durationMs` e `errorCode`.
- Corpo da requisição, headers `Authorization` e `X-Notification-Key`, JWT, chaves e credenciais de banco nunca são registrados nem encaminhados.
- A chave técnica de notificação possui no mínimo 32 caracteres, é comparada em tempo constante e fica apenas em variáveis sensíveis.
- O payload de notificação limita destinatário, assunto e corpo; nenhum desses campos é incluído em logs ou atributos de telemetria.
- O log de acesso do API Gateway contém somente campos técnicos; o encaminhador aplica allowlist antes de enviar ao New Relic.
- License keys e credenciais AWS ficam em variáveis sensíveis do HCP Terraform e em GitHub Environments, nunca no código.
- Nenhuma role IAM é criada: todas as Lambdas reutilizam a `LabRole`.

Uma evolução futura recomendada é adicionar segundo fator ou código de uso único.
