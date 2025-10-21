package com.ismile.core.notification.dto.websocket;

import com.ismile.core.notification.dto.BaseRequestDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for WebSocket notification requests.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WebSocketNotificationRequestDto extends BaseRequestDto {
    // Recipient here is the userId
}
