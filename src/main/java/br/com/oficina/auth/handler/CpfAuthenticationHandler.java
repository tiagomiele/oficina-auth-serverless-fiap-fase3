package br.com.oficina.auth.handler;

import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.Cpf;
import br.com.oficina.auth.infrastructure.ClienteRepository;
import br.com.oficina.auth.infrastructure.JwtService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class CpfAuthenticationHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final ClienteRepository clients;
  private final JwtService tokens;
  private final long ttlSeconds;

  public CpfAuthenticationHandler() {
    this(AuthConfig.loginFromEnvironment());
  }

  CpfAuthenticationHandler(AuthConfig config) {
    this.clients = new ClienteRepository(config);
    this.tokens = new JwtService(config);
    this.ttlSeconds = config.jwtTtlSeconds();
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
    String requestId = context.getAwsRequestId();
    try {
      String cpfRaw = requestCpf(event);
      if (cpfRaw == null || cpfRaw.isBlank()) {
        return error(400, "INVALID_REQUEST", "Informe o campo cpf.", requestId);
      }
      Cpf cpf = Cpf.parse(cpfRaw);
      return clients
          .findActiveClientId(cpf)
          .map(
              clientId -> {
                context.getLogger().log("auth_result=success requestId=" + requestId + "\n");
                return HttpResponses.json(
                    200,
                    Map.of(
                        "accessToken",
                        tokens.issue(clientId),
                        "tokenType",
                        "Bearer",
                        "expiresIn",
                        ttlSeconds));
              })
          .orElseGet(
              () -> {
                context.getLogger().log("auth_result=denied requestId=" + requestId + "\n");
                return authenticationFailed(requestId);
              });
    } catch (JsonProcessingException exception) {
      return error(400, "INVALID_REQUEST", "Corpo JSON inválido.", requestId);
    } catch (IllegalArgumentException exception) {
      context.getLogger().log("auth_result=denied requestId=" + requestId + "\n");
      return authenticationFailed(requestId);
    } catch (Exception exception) {
      context.getLogger().log("auth_result=error requestId=" + requestId + "\n");
      return error(500, "INTERNAL_ERROR", "Não foi possível processar a autenticação.", requestId);
    }
  }

  private static String requestCpf(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
    if (event == null || event.getBody() == null) {
      return null;
    }
    String body =
        Boolean.TRUE.equals(event.getIsBase64Encoded())
            ? new String(Base64.getDecoder().decode(event.getBody()), StandardCharsets.UTF_8)
            : event.getBody();
    return JSON.readTree(body).path("cpf").asText(null);
  }

  private static APIGatewayV2HTTPResponse authenticationFailed(String requestId) {
    return error(401, "AUTHENTICATION_FAILED", "Não foi possível autenticar o cliente.", requestId);
  }

  private static APIGatewayV2HTTPResponse error(
      int status, String code, String message, String requestId) {
    return HttpResponses.json(
        status, Map.of("code", code, "message", message, "requestId", requestId));
  }
}
