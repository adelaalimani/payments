package com.adela.payments.controller;

import com.adela.payments.auth.AuthenticationService;
import com.adela.payments.request.AuthenticationRequest;
import com.adela.payments.request.RefreshRequest;
import com.adela.payments.request.RegistrationRequest;
import com.adela.payments.response.AuthenticationResponse;
import com.adela.payments.validation.EmailDomainValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    private static final List<String> DISPOSABLE_DOMAINS = List.of(
            "10minutemail", "20minutemail", "33mail", "5ymail", "anonbox", "guerrillamail",
            "mailinator", "maildrop", "mailnesia", "moakt", "my10minutemail", "throwawaymail",
            "trashmail", "temp-mail", "tempmail", "truemail", "yopmail");

    @Mock
    private AuthenticationService authenticationService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setConstraintValidatorFactory(new TestConstraintValidatorFactory());
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(authenticationService))
                .setValidator(validator)
                .build();
    }

    private RegistrationRequest.RegistrationRequestBuilder validRegistration() {
        return RegistrationRequest.builder()
                .firstName("Adela")
                .lastName("Alimani")
                .email("adela@gmail.com")
                .phoneNumber("+4912389765634")
                .password("Password123!")
                .confirmPassword("Password123!");
    }

    // ---------- login ----------

    @Test
    void login_validRequest_returnsOk() throws Exception {
        AuthenticationRequest request = AuthenticationRequest.builder().email("adela@gmail.com").password("secretPass1!").build();
        AuthenticationResponse response = new AuthenticationResponse("access", "refresh", "Bearer");
        when(authenticationService.login(any(AuthenticationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access"))
                .andExpect(jsonPath("$.refresh_token").value("refresh"));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        String body = "{\"email\":\"\",\"password\":\"secretPass1!\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"password\":\"secretPass1!\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        String body = "{\"email\":\"adela@gmail.com\",\"password\":\"\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- register ----------

    @Test
    void register_validRequest_returnsCreated() throws Exception {
        RegistrationRequest request = validRegistration().build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegistrationRequest> captor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(authenticationService).register(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEmail()).isEqualTo(request.getEmail());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getFirstName()).isEqualTo(request.getFirstName());
    }

    @Test
    void register_missingFirstName_returns400() throws Exception {
        RegistrationRequest request = validRegistration().firstName("").build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_firstNameWithDigits_returns400() throws Exception {
        RegistrationRequest request = validRegistration().firstName("Adela123").build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidPhoneNumber_returns400() throws Exception {
        RegistrationRequest request = validRegistration().phoneNumber("not-a-phone").build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_weakPassword_returns400() throws Exception {
        RegistrationRequest request = validRegistration().password("weakpass").confirmPassword("weakpass").build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_disposableEmailDomain_returns400() throws Exception {
        RegistrationRequest request = validRegistration().email("someone@mailinator.com").build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------- refresh ----------

    @Test
    void refresh_validRequest_returnsOk() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("old-refresh-token").build();
        AuthenticationResponse response = new AuthenticationResponse("new-access", "old-refresh-token", "Bearer");
        when(authenticationService.refreshToken(any(RefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new-access"));
    }

    /**
     * EmailDomainValidator is normally instantiated by Spring with a {@code @Value}-injected list;
     * outside a Spring context, Bean Validation's default factory can't satisfy that constructor.
     * This factory supplies an equivalent instance so registration validation behaves like production.
     */
    private static class TestConstraintValidatorFactory implements ConstraintValidatorFactory {
        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key == EmailDomainValidator.class) {
                @SuppressWarnings("unchecked")
                T instance = (T) new EmailDomainValidator(DISPOSABLE_DOMAINS);
                return instance;
            }
            try {
                return key.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot instantiate validator " + key, e);
            }
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
        }
    }
}