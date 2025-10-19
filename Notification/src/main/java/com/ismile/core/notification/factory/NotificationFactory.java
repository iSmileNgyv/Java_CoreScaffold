package com.ismile.core.notification.factory;

import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.service.NotificationService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A factory component to provide the correct notification service based on the delivery method.
 * This class uses constructor injection to gather all beans that implement NotificationService
 * and maps them to their corresponding DeliveryMethod.
 */
@Component
public class NotificationFactory {

    private final Map<DeliveryMethod, NotificationService<?, ?>> serviceMap;

    /**
     * Constructor that injects all available NotificationService beans.
     * It iterates through the services and populates a map where the key is the
     * delivery method and the value is the service instance.
     *
     * @param services A list of all beans implementing the NotificationService interface.
     */
    public NotificationFactory(List<NotificationService<?, ?>> services) {
        serviceMap = new EnumMap<>(DeliveryMethod.class);
        for (NotificationService<?, ?> service : services) {
            if (service.getDeliveryMethod() != null) {
                serviceMap.put(service.getDeliveryMethod(), service);
            }
        }
    }

    /**
     * Retrieves the appropriate notification service for a given delivery method.
     *
     * @param deliveryMethod The enum representing the desired delivery method (e.g., EMAIL).
     * @return An Optional containing the NotificationService if found, otherwise an empty Optional.
     */
    public Optional<NotificationService<?, ?>> getService(DeliveryMethod deliveryMethod) {
        return Optional.ofNullable(serviceMap.get(deliveryMethod));
    }
}
