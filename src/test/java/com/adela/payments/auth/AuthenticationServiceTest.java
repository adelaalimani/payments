package com.adela.payments.auth;

import com.adela.payments.entity.Role;
import com.adela.payments.entity.User;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.mapper.UserMapper;
import com.adela.payments.repository.RoleRepository;
import com.adela.payments.repository.UserRepository;
import com.adela.payments.request.AuthenticationRequest;
import com.adela.payments.request.RefreshRequest;
import com.adela.payments.request.RegistrationRequest;
import com.adela.payments.response.AuthenticationResponse;
import com.adela.payments.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserMapper userMapper;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(authenticationManager, jwtService, userRepository, roleRepository, userMapper);
    }

    // ---------- login ----------

    @Test
    void login_success_returnsAccessAndRefreshTokens() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("adela@gmail.com")
                .password("password")
                .build();
        User user = User.builder().id(1L).email("adela@gmail.com").password("hashed").build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateAccessToken("adela@gmail.com")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("adela@gmail.com")).thenReturn("refresh-token");

        AuthenticationResponse response = authenticationService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_invalidCredentials_propagatesAuthenticationException() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("adela@gmail.com")
                .password("wrong")
                .build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwtService);
    }

    // ---------- register ----------

    @Test
    void register_success_savesUserWithCustomerRole() {
        RegistrationRequest request = RegistrationRequest.builder()
                .firstName("Adela")
                .lastName("Alimani")
                .email("new@gmail.com")
                .phoneNumber("+10000000000")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();
        Role customerRole = Role.builder().id(1L).name("CUSTOMER").build();
        User mappedUser = User.builder().firstName("Adela").lastName("Alimani").email("new@gmail.com").build();

        when(userRepository.existsByEmailIgnoreCase("new@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+10000000000")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userMapper.toUser(request)).thenReturn(mappedUser);

        authenticationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly(customerRole);
    }

    @Test
    void register_emailAlreadyExists_throwsBadRequest() {
        RegistrationRequest request = registrationRequest("existing@gmail.com", "+10000000000");
        when(userRepository.existsByEmailIgnoreCase("existing@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_phoneNumberAlreadyExists_throwsBadRequest() {
        RegistrationRequest request = registrationRequest("new@gmail.com", "+10000000000");
        when(userRepository.existsByEmailIgnoreCase("new@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+10000000000")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordMismatch_throwsBadRequest() {
        RegistrationRequest request = RegistrationRequest.builder()
                .firstName("Adela")
                .lastName("Alimani")
                .email("new@gmail.com")
                .phoneNumber("+10000000000")
                .password("Password123!")
                .confirmPassword("Different123!")
                .build();
        when(userRepository.existsByEmailIgnoreCase("new@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+10000000000")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Passwords do not match");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void register_customerRoleMissing_throwsBadRequest() {
        RegistrationRequest request = registrationRequest("new@gmail.com", "+10000000000");
        when(userRepository.existsByEmailIgnoreCase("new@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+10000000000")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Role CUSTOMER not found");
        verify(userRepository, never()).save(any());
    }

    private RegistrationRequest registrationRequest(String email, String phoneNumber) {
        return RegistrationRequest.builder()
                .firstName("Adela")
                .lastName("Alimani")
                .email(email)
                .phoneNumber(phoneNumber)
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();
    }

    // ---------- refreshToken ----------

    @Test
    void refreshToken_success_returnsNewAccessTokenAndSameRefreshToken() {
        RefreshRequest request = RefreshRequest.builder().refreshToken("old-refresh-token").build();
        when(jwtService.refreshAccessToken("old-refresh-token")).thenReturn("new-access-token");

        AuthenticationResponse response = authenticationService.refreshToken(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("old-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void refreshToken_invalidToken_propagatesException() {
        RefreshRequest request = RefreshRequest.builder().refreshToken("bad-token").build();
        when(jwtService.refreshAccessToken("bad-token")).thenThrow(new BadRequestException("Invalid JWT token"));

        assertThatThrownBy(() -> authenticationService.refreshToken(request))
                .isInstanceOf(BadRequestException.class);
    }
}