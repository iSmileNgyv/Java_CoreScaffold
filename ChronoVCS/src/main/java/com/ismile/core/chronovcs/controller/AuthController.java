package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.web.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * CLI-nin "self" üçün istifadə etdiyi endpoint.
     * Basic auth filter-də yoxlanır, burda sadəcə CurrentUser qaytarırıq.
     */
    @GetMapping("/self")
    public ResponseEntity<AuthenticatedUser> self(
            @CurrentUser AuthenticatedUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(user);
    }
}