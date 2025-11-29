package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.push.PushRequestDto;
import com.ismile.core.chronovcs.dto.push.PushResultDto;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.versioning.PushService;
import com.ismile.core.chronovcs.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @PostMapping("/{repoKey}/push")
    public ResponseEntity<PushResultDto> push(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @RequestBody PushRequestDto request
    ) {
        PushResultDto result = pushService.push(user, repoKey, request);
        return ResponseEntity.ok(result);
    }
}