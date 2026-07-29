package com.adela.payments.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthenticationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(example = "adela@gmail.com")
    String email;

    @NotBlank(message = "Password is required")
    @Schema(example = "pAssword123!_")
    String password;
}
