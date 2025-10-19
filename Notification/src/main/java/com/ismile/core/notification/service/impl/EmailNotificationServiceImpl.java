package com.ismile.core.notification.service.impl;

import com.ismile.core.notification.dto.email.EmailNotificationRequestDto;
import com.ismile.core.notification.dto.email.EmailNotificationResponseDto;
import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.repository.UserSettingsRepository;
import com.ismile.core.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    /**
     * Implements the method from the NotificationService interface.
     * Declares that this service handles EMAIL notifications.
     */
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
            // Re-throwing as a runtime exception to be handled by the calling gRPC service.
            throw new RuntimeException("Email sending failed", e);
        }
    }

    /**
     * Listens for OTP messages from the 'otp_1' Kafka topic.
     * This method is responsible for processing the message and sending an email.
     * Acknowledgment is handled carefully to prevent message re-delivery on failure.
     *
     * @param message The message consumed from Kafka, as a Map.
     * @param acknowledgment The acknowledgment object to confirm message processing.
     */
    @KafkaListener(topics = "otp_1", groupId = "notification_group")
    private void listenForOtpNotification(Map<String, Object> message, Acknowledgment acknowledgment) {
        log.info("Received OTP notification message from Kafka: {}", message);
        try {
            // Extract data safely from the message map
            int userId = (int) message.get("userId");
            String deliveryMethodStr = (String) message.get("deliveryMethod");
            String code = message.get("code").toString();

            // Convert delivery method string to enum
            DeliveryMethod deliveryMethod = DeliveryMethod.valueOf(deliveryMethodStr);

            // Find the user's settings. We only process if delivery method is EMAIL.
            var userSettingsOpt = userSettingsRepository.findByUserIdAndDeliveryMethod(userId, deliveryMethod);

            if (userSettingsOpt.isEmpty()) {
                // If user settings are not found or delivery method is not email,
                // we still acknowledge the message to remove it from the queue.
                log.warn("User settings not found for userId: {} with delivery method: {}. Discarding message.", userId, deliveryMethod);
                acknowledgment.acknowledge();
                return;
            }

            var user = userSettingsOpt.get();

            EmailNotificationRequestDto request = new EmailNotificationRequestDto();
            request.setSubject("Your One-Time Password (OTP)");
            request.setRecipient(user.getRecipient()); // The user's email address
            request.setMessage("Your verification code is: " + code);

            this.send(request);

        } catch (Exception ex) {
            // Log the full exception for debugging purposes.
            log.error("Error processing OTP notification from Kafka. Message: {}. Error: {}", message, ex.getMessage(), ex);
            // In case of an error, we still acknowledge the message to avoid an infinite retry loop
            // for a potentially "poison pill" message. You might want a more sophisticated
            // dead-letter queue (DLQ) strategy for production.
        } finally {
            // Always acknowledge the message to prevent it from being re-processed.
            acknowledgment.acknowledge();
            log.debug("Kafka message acknowledged.");
        }
    }
}
