package com.ismile.core.notification.repository;
import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.entity.UserSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {
    Optional<UserSettingsEntity> findByUserIdAndDeliveryMethod(long userId, DeliveryMethod deliveryMethod);
}
