package br.com.oficina.auth.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public final class SnsNotificationPublisher implements NotificationPublisher {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SnsClient client;
  private final String topicArn;

  public SnsNotificationPublisher(SnsClient client, String topicArn) {
    this.client = client;
    this.topicArn = topicArn;
  }

  @Override
  public void publish(NotificationMessage message) {
    try {
      client.publish(
          PublishRequest.builder()
              .topicArn(topicArn)
              .message(JSON.writeValueAsString(message))
              .build());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Não foi possível serializar a notificação", exception);
    }
  }
}
