package dev.kanakku.identity.config;

import dev.kanakku.identity.repository.NoSmsGateway;
import dev.kanakku.identity.repository.WebhookSmsGateway;
import dev.kanakku.identity.service.SmsGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the SMS provider: the HTTP webhook when {@code kanakku.sms.webhook-url}
 * (KANAKKU_SMS_WEBHOOK_URL) is set, otherwise none, in which case phone numbers stay unverified.
 */
@Configuration
class SmsConfig {

  @Bean
  SmsGateway smsGateway(
      @Value("${kanakku.sms.webhook-url:}") String url,
      @Value("${kanakku.sms.webhook-token:}") String token) {
    if (url == null || url.isBlank()) {
      return new NoSmsGateway();
    }
    return new WebhookSmsGateway(url, token);
  }
}
