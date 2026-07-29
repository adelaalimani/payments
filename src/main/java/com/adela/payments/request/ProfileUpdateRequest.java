package com.adela.payments.request;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest {
    private String firstName;
    private String lastName;
    private LocalDateTime dateOfBirth;
}
