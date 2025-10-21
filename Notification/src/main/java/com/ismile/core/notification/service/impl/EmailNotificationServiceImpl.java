package com.ismile.core.notification.service.impl;

import com.ismile.core.notification.dto.email.EmailNotificationRequestDto;
import com.ismile.core.notification.dto.email.EmailNotificationResponseDto;
import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.entity.UserSettingsEntity;
import com.ismile.core.notification.repository.UserSettingsRepository;
import com.ismile.core.notification.service.NotificationService;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification.SendNotificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationServiceImpl implements NotificationService<EmailNotificationRequestDto, EmailNotificationResponseDto> {

    private final JavaMailSender javaMailSender;
    private final UserSettingsRepository userSettingsRepository;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Override
    public EmailNotificationResponseDto send(EmailNotificationRequestDto request) {
        var response = new EmailNotificationResponseDto();
        try {
            this.sendEmail(request.getRecipient(), request.getSubject(), request.getMessage());
            response.setEmailStatus("SENT");
        } catch (Exception e) {
            response.setEmailStatus("FAILED");
            log.error("Failed to send email via direct send method to: {}", request.getRecipient(), e);
        }
        return response;
    }

    @Override
    public DeliveryMethod getDeliveryMethod() {
        return DeliveryMethod.EMAIL;
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}. Error: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    /**
     * Listens for messages on the 'otp_email' topic.
     * This listener is now specific to email-based OTPs.
     * It receives a message body prepared by the OTP service.
     */
    @KafkaListener(topics = "otp_email", groupId = "notification_group_email")
    public void listenForOtpEmail(Map<String, Object> message, Acknowledgment acknowledgment) {
        log.info("Received OTP for EMAIL from Kafka: {}", message);
        try {
            int userId = (int) message.get("userId");
            String messageBody = (String) message.get("messageBody");
            String subject = (String) message.getOrDefault("subject", "Notification");

            var userSettings = userSettingsRepository.findByUserIdAndDeliveryMethod(userId, DeliveryMethod.EMAIL)
                    .orElseThrow(() -> new IllegalStateException("User settings for EMAIL not found for user ID: " + userId));

            // Use the recipient and prepared message body directly
            sendEmail(userSettings.getRecipient(), subject, messageBody);

        } catch (Exception ex) {
            log.error("Error processing OTP email from Kafka. Message: {}. Error: {}", message, ex.getMessage(), ex);
            // In a production environment, you would have a dead-letter queue (DLQ) strategy here.
        } finally {
            acknowledgment.acknowledge();
            log.debug("Kafka message acknowledged for topic 'otp_email'.");
        }
    }

    @Override
    public void processGrpcRequest(SendNotificationRequest grpcRequest) {
        // This method remains for direct gRPC calls if needed, but the primary flow is now via Kafka.
        log.info("Processing gRPC request for EMAIL to user_id: {}", grpcRequest.getUserId());
        UserSettingsEntity userSettings = userSettingsRepository.findByUserIdAndDeliveryMethod(grpcRequest.getUserId(), getDeliveryMethod())
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("User settings not found for the given user_id and delivery method.")
                        .asRuntimeException());

        EmailNotificationRequestDto emailRequest = new EmailNotificationRequestDto();
        emailRequest.setRecipient(userSettings.getRecipient());
        emailRequest.setMessage(grpcRequest.getMessageBody());
        emailRequest.setSubject(grpcRequest.getSubject());
        emailRequest.setRequestId(UUID.randomUUID());

        this.send(emailRequest);
    }
}
