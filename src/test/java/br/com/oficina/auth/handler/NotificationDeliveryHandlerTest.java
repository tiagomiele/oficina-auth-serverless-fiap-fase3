package br.com.oficina.auth.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.oficina.auth.notification.NotificationMessage;
import br.com.oficina.auth.observability.NoopTelemetry;
import br.com.oficina.auth.support.RecordingContext;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationDeliveryHandlerTest {

  private final RecordingContext context = new RecordingContext("lambda-request-1");

  @Test
  void deliversSnsMessageWithoutLoggingPersonalData() {
    AtomicReference<NotificationMessage> delivered = new AtomicReference<>();
    NotificationDeliveryHandler handler =
        new NotificationDeliveryHandler(delivered::set, new NoopTelemetry());

    String result = handler.handleRequest(event(validMessage()), context);

    assertEquals("delivered=1", result);
    assertEquals("cliente@example.com", delivered.get().destination());
    assertFalse(context.logLines().toString().contains("cliente@example.com"));
    assertFalse(context.logLines().toString().contains("Status atualizado"));
  }

  @Test
  void throwsOnInvalidMessageSoSnsCanRetryAndRedrive() {
    NotificationDeliveryHandler handler =
        new NotificationDeliveryHandler(message -> {}, new NoopTelemetry());

    assertThrows(IllegalArgumentException.class, () -> handler.handleRequest(event("{}"), context));
  }

  private static SNSEvent event(String message) {
    SNSEvent.SNS sns = new SNSEvent.SNS();
    sns.setMessage(message);
    SNSEvent.SNSRecord record = new SNSEvent.SNSRecord();
    record.setSns(sns);
    SNSEvent event = new SNSEvent();
    event.setRecords(List.of(record));
    return event;
  }

  private static String validMessage() {
    return """
        {"destination":"cliente@example.com","subject":"OS atualizada","body":"Status atualizado","requestId":"client-request-1"}
        """;
  }
}
