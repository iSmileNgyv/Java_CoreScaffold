package com.ismile.core.notification.service;

import com.ismile.core.notification.dto.BaseRequestDto;
import com.ismile.core.notification.dto.BaseResponseDto;

public interface NotificationService<T extends BaseRequestDto, R extends BaseResponseDto> {
    R send(T request);
}