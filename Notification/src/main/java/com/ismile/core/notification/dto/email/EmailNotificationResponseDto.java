package com.ismile.core.notification.dto.email;

import com.ismile.core.notification.dto.BaseResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailNotificationResponseDto extends BaseResponseDto {
    private String emailStatus;
}