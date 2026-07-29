package com.adela.payments.request;

import com.adela.payments.validation.NonDisposableEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegistrationRequest {

    @NotBlank(message = "First name is required")
    @Size(
            min = 1,
            max = 50,
            message = "First name must be between {min} and {max} characters"
    )
    @Pattern(
            regexp = "^[\\p{L} '-]+$",
            message = "First name can only contain letters, spaces, hyphens and apostrophes"
    )
    @Schema(example = "Adela")
    String firstName;

    @NotBlank(message = "Last name is required")
    @Size(
            min = 1,
            max = 50,
            message = "Last name must be between {min} and {max} characters"
    )
    @Pattern(
            regexp = "^[\\p{L} '-]+$",
            message = "Last name can only contain letters, spaces, hyphens and apostrophes"
    )
    @Schema(example = "Alimani")
    String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @NonDisposableEmail(message = "Disposable email addresses are not allowed")
    @Schema(example = "adela@gmail.com")
    String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "Phone number must be a valid number in E.164 format, e.g. +4912389765634"
    )
    @Schema(example = "+4912389765634")
    String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8,
            max = 72,
            message = "Password must be between {min} and {max} characters"
    )
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*\\W).*$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit and one special character"
    )
    @Schema(example = "pAssword123!_")
    String password;

    @NotBlank(message = "Confirm password is required")
    @Size(min = 8,
            max = 72,
            message = "Confirm password must be between {min} and {max} characters"
    )
    @Schema(example = "pAssword123!_")
    String confirmPassword;

}
