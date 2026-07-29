package com.adela.payments.controller;

import com.adela.payments.entity.User;
import com.adela.payments.request.ChangePasswordRequest;
import com.adela.payments.request.ProfileUpdateRequest;
import com.adela.payments.user_details.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User API")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PatchMapping
    public ResponseEntity<Void> updateProfile(
            @RequestBody
            @Valid
            final ProfileUpdateRequest request,
            final Authentication principal) {
        this.service.updateProfile(request, getUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody
            @Valid
            final ChangePasswordRequest request,
            final Authentication principal) {
        this.service.changePassword(request, getUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/deactivate")
    public ResponseEntity<Void> deactivateAccount(final Authentication principal) {
        this.service.deactivateAccount(getUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reactivate")
    public ResponseEntity<Void> reactivateAccount(final Authentication principal) {
        this.service.reactivateAccount(getUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(final Authentication principal) {
        this.service.deleteAccount(getUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(final Authentication authentication) {
        return ((User) Objects.requireNonNull(authentication.getPrincipal())).getId();

    }

}
