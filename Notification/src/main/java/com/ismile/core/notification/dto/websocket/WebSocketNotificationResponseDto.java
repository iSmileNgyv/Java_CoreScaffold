package com.ismile.core.notification.dto.websocket;

import com.ismile.core.notification.dto.BaseResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for WebSocket notification responses.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketNotificationResponseDto extends BaseResponseDto {
    private String status;
    private int deliveredSessions;
}
