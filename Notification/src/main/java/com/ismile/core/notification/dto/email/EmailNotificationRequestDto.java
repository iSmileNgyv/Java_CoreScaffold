package com.ismile.core.notification.dto.email;

import com.ismile.core.notification.dto.BaseRequestDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailNotificationRequestDto extends BaseRequestDto {
    private String subject;
}
