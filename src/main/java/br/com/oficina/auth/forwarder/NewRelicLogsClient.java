package br.com.oficina.auth.forwarder;

import java.util.List;
import java.util.Map;

public interface NewRelicLogsClient {

  void send(List<Map<String, Object>> sanitizedLogs);
}
