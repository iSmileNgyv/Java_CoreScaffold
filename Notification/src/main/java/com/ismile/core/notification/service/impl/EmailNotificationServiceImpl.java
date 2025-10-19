package com.ismile.core.notification.service.impl;

import com.ismile.core.notification.dto.email.EmailNotificationRequestDto;
import com.ismile.core.notification.dto.email.EmailNotificationResponseDto;
import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.repository.UserSettingsRepository;
import com.ismile.core.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public EmailNotificationResponseDto send(EmailNotificationRequestDto request) {
        var response = new EmailNotificationResponseDto();
        this.sendEmail(request.getRecipient(), request.getSubject(), request.getMessage());
        return response;
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("ismayilnagiyev100@gmail.com"); // This should match the username in application.yml
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}. Error: {}", to, e.getMessage());
            // You might want to re-throw this as a custom exception
            // depending on your error handling strategy.
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
