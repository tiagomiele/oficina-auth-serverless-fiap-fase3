package br.com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

final class HttpResponses {

  private static final ObjectMapper JSON = new ObjectMapper();

  private HttpResponses() {}

  static APIGatewayV2HTTPResponse json(int status, Object body) {
    try {
      return APIGatewayV2HTTPResponse.builder()
          .withStatusCode(status)
          .withHeaders(
              Map.of(
                  "Content-Type", "application/json",
                  "Cache-Control", "no-store"))
          .withBody(JSON.writeValueAsString(body))
          .build();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar resposta", exception);
    }
  }
}
