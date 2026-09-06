package br.com.oficina.auth.forwarder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApiGatewayLogForwarder {

  private final NewRelicLogsClient client;

  public ApiGatewayLogForwarder(NewRelicLogsClient client) {
    this.client = client;
  }

  /**
   * Envia somente os registros de acesso reconhecidos e sanitizados. Retorna quantos foram aceitos.
   */
  public int forward(CloudWatchLogsPayload payload) {
    List<Map<String, Object>> sanitized =
        payload.records().stream()
            .map(record -> AccessLogSanitizer.sanitize(record.message()))
            .flatMap(Optional::stream)
            .toList();
    client.send(sanitized);
    return sanitized.size();
  }
}
