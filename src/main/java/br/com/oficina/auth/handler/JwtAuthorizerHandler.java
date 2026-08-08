package br.com.oficina.auth.handler;

import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.infrastructure.JwtService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.jsonwebtoken.Claims;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JwtAuthorizerHandler
    implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  private final JwtService tokens;

  public JwtAuthorizerHandler() {
    this.tokens = new JwtService(AuthConfig.authorizerFromEnvironment());
  }

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    String requestId = context.getAwsRequestId();
    try {
      String token = bearerToken(event);
      Claims claims = tokens.verify(token);
      Map<String, Object> authorizerContext = new LinkedHashMap<>();
      authorizerContext.put("subject", claims.getSubject());
      authorizerContext.put("clientId", claims.get("client_id", Long.class));
      authorizerContext.put("role", claims.get("role", String.class));
      context.getLogger().log("authorization_result=allow requestId=" + requestId + "\n");
      return Map.of("isAuthorized", true, "context", authorizerContext);
    } catch (Exception exception) {
      context.getLogger().log("authorization_result=deny requestId=" + requestId + "\n");
      return Map.of("isAuthorized", false, "context", Map.of("requestId", requestId));
    }
  }

  private static String bearerToken(Map<String, Object> event) {
    if (event == null) {
      throw new IllegalArgumentException("Evento ausente");
    }
    Object authorizationToken = event.get("authorizationToken");
    if (authorizationToken instanceof String value) {
      return stripBearer(value);
    }
    Object headers = event.get("headers");
    if (headers instanceof Map<?, ?> values) {
      for (Map.Entry<?, ?> entry : values.entrySet()) {
        if (entry.getKey() instanceof String key
            && "authorization".equalsIgnoreCase(key)
            && entry.getValue() instanceof String value) {
          return stripBearer(value);
        }
      }
    }
    Object identitySource = event.get("identitySource");
    if (identitySource instanceof Collection<?> values && !values.isEmpty()) {
      Object first = values.iterator().next();
      if (first instanceof String value) {
        return stripBearer(value);
      }
    }
    throw new IllegalArgumentException("Bearer token ausente");
  }

  private static String stripBearer(String value) {
    if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new IllegalArgumentException("Bearer token inválido");
    }
    return value.substring(7).trim();
  }
}
