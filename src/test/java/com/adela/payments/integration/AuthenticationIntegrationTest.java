package com.adela.payments.integration;

import com.adela.payments.entity.User;
import com.adela.payments.request.RegistrationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void register_success_persistsUserWithCustomerRole() throws Exception {
        register("auth-register-1@gmail.com", "+10000010001");

        Optional<User> saved = userRepository.findByEmailIgnoreCase("auth-register-1@gmail.com");
        assertThat(saved).isPresent();
        assertThat(saved.get().getRoles()).extracting("name").containsExactly("CUSTOMER");
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() throws Exception {
        register("auth-register-2@gmail.com", "+10000010002");
        RegistrationRequest duplicate = RegistrationRequest.builder()
                .firstName("Another").lastName("User")
                .email("auth-register-2@gmail.com")
                .phoneNumber("+10000010099")
                .password(DEFAULT_PASSWORD).confirmPassword(DEFAULT_PASSWORD)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicatePhoneNumber_returnsBadRequest() throws Exception {
        register("auth-register-3@gmail.com", "+10000010003");
        RegistrationRequest duplicate = RegistrationRequest.builder()
                .firstName("Another").lastName("User")
                .email("auth-register-3-b@gmail.com")
                .phoneNumber("+10000010003")
                .password(DEFAULT_PASSWORD).confirmPassword(DEFAULT_PASSWORD)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordMismatch_returnsBadRequest() throws Exception {
        RegistrationRequest request = RegistrationRequest.builder()
                .firstName("Test").lastName("User")
                .email("auth-register-4@gmail.com")
                .phoneNumber("+10000010004")
                .password("Password123!").confirmPassword("Different123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidPayload_returnsBadRequest() throws Exception {
        String body = "{\"firstName\":\"\",\"lastName\":\"User\",\"email\":\"bad@gmail.com\"," +
                "\"phoneNumber\":\"+10000010005\",\"password\":\"Password123!\",\"confirmPassword\":\"Password123!\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_success_returnsAccessAndRefreshTokens() throws Exception {
        register("auth-login-1@gmail.com", "+10000020001");

        String body = "{\"email\":\"auth-login-1@gmail.com\",\"password\":\"" + DEFAULT_PASSWORD + "\"}";

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.get("access_token").asText()).isNotBlank();
    }

    @Test
    void login_wrongPassword_isRejected() throws Exception {
        register("auth-login-2@gmail.com", "+10000020002");

        String body = "{\"email\":\"auth-login-2@gmail.com\",\"password\":\"WrongPassword1!\"}";

        // AuthenticationException from the AuthenticationManager isn't mapped by any specific
        // @ExceptionHandler in ExceptionAdvice, so it falls through to the generic 500 handler.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void login_unknownEmail_isRejected() throws Exception {
        String body = "{\"email\":\"does-not-exist@gmail.com\",\"password\":\"" + DEFAULT_PASSWORD + "\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void refresh_success_returnsNewAccessToken() throws Exception {
        register("auth-refresh-1@gmail.com", "+10000030001");
        String body = "{\"email\":\"auth-refresh-1@gmail.com\",\"password\":\"" + DEFAULT_PASSWORD + "\"}";
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginResponse).get("refresh_token").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").value(refreshToken));
    }

    @Test
    void refresh_invalidToken_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isBadRequest());
    }
}
