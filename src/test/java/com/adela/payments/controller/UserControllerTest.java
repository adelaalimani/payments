package com.adela.payments.controller;

import com.adela.payments.entity.User;
import com.adela.payments.request.ChangePasswordRequest;
import com.adela.payments.request.ProfileUpdateRequest;
import com.adela.payments.user_details.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    private Authentication authenticationFor(Long userId) {
        User user = User.builder().id(userId).email("user" + userId + "@gmail.com").build();
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    @Test
    void updateProfile_returnsNoContentAndDelegatesWithAuthenticatedUserId() throws Exception {
        ProfileUpdateRequest request = ProfileUpdateRequest.builder().firstName("NewFirst").lastName("NewLast").build();

        mockMvc.perform(patch("/api/v1/users")
                        .principal(authenticationFor(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).updateProfile(any(ProfileUpdateRequest.class), eq(7L));
    }

    @Test
    void changePassword_returnsNoContentAndDelegatesWithAuthenticatedUserId() throws Exception {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass1!")
                .newPassword("newPass1!")
                .confirmNewPassword("newPass1!")
                .build();

        mockMvc.perform(post("/api/v1/users/password")
                        .principal(authenticationFor(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(any(ChangePasswordRequest.class), eq(8L));
    }

    @Test
    void deactivateAccount_returnsNoContentAndDelegatesWithAuthenticatedUserId() throws Exception {
        mockMvc.perform(patch("/api/v1/users/deactivate")
                        .principal(authenticationFor(9L)))
                .andExpect(status().isNoContent());

        verify(userService).deactivateAccount(9L);
    }

    @Test
    void reactivateAccount_returnsNoContentAndDelegatesWithAuthenticatedUserId() throws Exception {
        mockMvc.perform(patch("/api/v1/users/reactivate")
                        .principal(authenticationFor(10L)))
                .andExpect(status().isNoContent());

        verify(userService).reactivateAccount(10L);
    }

    @Test
    void deleteAccount_returnsNoContentAndDelegatesWithAuthenticatedUserId() throws Exception {
        mockMvc.perform(delete("/api/v1/users")
                        .principal(authenticationFor(11L)))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(11L);
    }
}
