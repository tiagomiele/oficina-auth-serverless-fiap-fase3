package br.com.oficina.auth.handler;

import br.com.oficina.auth.notification.NotificationMessage;
import br.com.oficina.auth.notification.NotificationSender;
import br.com.oficina.auth.notification.SesNotificationSender;
import br.com.oficina.auth.observability.StructuredLogger;
import br.com.oficina.auth.observability.Telemetry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sesv2.SesV2Client;

public final class NotificationDeliveryHandler implements RequestHandler<SNSEvent, String> {

  private static final String FUNCTION = "notification-delivery";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final NotificationSender sender;
  private final Telemetry telemetry;

  public NotificationDeliveryHandler() {
    this(
        new SesNotificationSender(
            SesV2Client.create(), requiredEnvironment("NOTIFICATION_SOURCE_EMAIL")),
        Telemetry.fromEnvironment());
  }

  NotificationDeliveryHandler(NotificationSender sender, Telemetry telemetry) {
    this.sender = sender;
    this.telemetry = telemetry;
  }

  @Override
  public String handleRequest(SNSEvent event, Context context) {
    long startedAt = System.nanoTime();
    String requestId = context.getAwsRequestId();
    StructuredLogger logger =
        new StructuredLogger(FUNCTION, telemetry, line -> context.getLogger().log(line + "\n"));
    try {
      int delivered = 0;
      if (event == null || event.getRecords() == null || event.getRecords().isEmpty()) {
        throw new IllegalArgumentException("Evento SNS vazio");
      }
      for (SNSEvent.SNSRecord record : event.getRecords()) {
        NotificationMessage message = parse(record);
        requestId = message.requestId();
        telemetry.addAttribute("request.id", requestId);
        sender.send(message);
        delivered++;
      }
      telemetry.addAttribute("notification.delivered", Integer.toString(delivered));
      logger.log("DELIVERED", requestId, elapsedMillis(startedAt), null);
      return "delivered=" + delivered;
    } catch (RuntimeException exception) {
      logger.log("ERROR", requestId, elapsedMillis(startedAt), "DELIVERY_FAILED");
      throw exception;
    }
  }

  private static NotificationMessage parse(SNSEvent.SNSRecord record) {
    if (record == null || record.getSNS() == null || record.getSNS().getMessage() == null) {
      throw new IllegalArgumentException("Registro SNS inválido");
    }
    try {
      return JSON.readValue(record.getSNS().getMessage(), NotificationMessage.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Mensagem SNS inválida", exception);
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " não configurada");
    }
    return value;
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
