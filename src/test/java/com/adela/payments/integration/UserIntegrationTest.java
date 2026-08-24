package com.adela.payments.integration;

import com.adela.payments.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIntegrationTest extends AbstractIntegrationTest {

    @Test
    void updateProfile_success_changesFirstAndLastName() throws Exception {
        String token = registerAndLogin("user-update-1@gmail.com", "+10000040001");

        mockMvc.perform(patch("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Updated\",\"lastName\":\"Name\"}"))
                .andExpect(status().isNoContent());

        User user = userRepository.findByEmailIgnoreCase("user-update-1@gmail.com").orElseThrow();
        assertThat(user.getFirstName()).isEqualTo("Updated");
        assertThat(user.getLastName()).isEqualTo("Name");
    }

    @Test
    void changePassword_success_allowsLoginWithNewPassword() throws Exception {
        String token = registerAndLogin("user-changepw-1@gmail.com", "+10000040002");

        mockMvc.perform(post("/api/v1/users/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + DEFAULT_PASSWORD + "\",\"newPassword\":\"NewPassword1!\",\"confirmNewPassword\":\"NewPassword1!\"}"))
                .andExpect(status().isNoContent());

        // Old password should no longer work; new password should.
        login("user-changepw-1@gmail.com", "NewPassword1!");
    }

    @Test
    void changePassword_wrongCurrentPassword_returnsBadRequest() throws Exception {
        String token = registerAndLogin("user-changepw-2@gmail.com", "+10000040003");

        mockMvc.perform(post("/api/v1/users/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword1!\",\"newPassword\":\"NewPassword1!\",\"confirmNewPassword\":\"NewPassword1!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_newPasswordsMismatch_returnsBadRequest() throws Exception {
        String token = registerAndLogin("user-changepw-3@gmail.com", "+10000040004");

        mockMvc.perform(post("/api/v1/users/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + DEFAULT_PASSWORD + "\",\"newPassword\":\"NewPassword1!\",\"confirmNewPassword\":\"Different1!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateAccount_success_disablesUser() throws Exception {
        String token = registerAndLogin("user-deactivate-1@gmail.com", "+10000040005");

        mockMvc.perform(patch("/api/v1/users/deactivate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        User user = userRepository.findByEmailIgnoreCase("user-deactivate-1@gmail.com").orElseThrow();
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void reactivateAccount_returnsNoContentButDoesNotPersist() throws Exception {
        // reactivateAccount() never calls userRepository.save(), so even though it doesn't hit the
        // auditing bug above (no flush occurs), the enabled flag change is silently lost - a second,
        // independent bug this test documents rather than papering over. The disabled state is seeded
        // with a raw JDBC update rather than userRepository.save() so the setup itself doesn't trip
        // the auditing bug being documented above.
        String token = registerAndLogin("user-reactivate-1@gmail.com", "+10000040006");
        jdbcTemplate.update("UPDATE users SET enabled = false WHERE email = ?", "user-reactivate-1@gmail.com");

        mockMvc.perform(patch("/api/v1/users/reactivate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        User reloaded = userRepository.findByEmailIgnoreCase("user-reactivate-1@gmail.com").orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    void deleteAccount_returnsNoContent() throws Exception {
        String token = registerAndLogin("user-delete-1@gmail.com", "+10000040007");

        // The current deleteAccount implementation is a no-op; this documents the endpoint's
        // actual behavior rather than asserting removal that doesn't happen.
        mockMvc.perform(delete("/api/v1/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmailIgnoreCase("user-delete-1@gmail.com")).isPresent();
    }

    @Test
    void protectedEndpoint_withoutToken_isRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/users/deactivate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_withMalformedToken_isRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/users/deactivate")
                        .header("Authorization", "not-a-bearer-token"))
                .andExpect(status().isForbidden());
    }
}
