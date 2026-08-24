package com.adela.payments.service;

import com.adela.payments.entity.User;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.mapper.UserMapper;
import com.adela.payments.repository.UserRepository;
import com.adela.payments.request.ChangePasswordRequest;
import com.adela.payments.request.ProfileUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;

    private UserServiceImpl userService;

    private UserServiceImpl service() {
        return new UserServiceImpl(userRepository, passwordEncoder, userMapper);
    }

    private User existingUser() {
        return User.builder()
                .id(1L)
                .firstName("Adela")
                .lastName("Alimani")
                .email("adela@gmail.com")
                .phoneNumber("+10000000000")
                .password("encoded-old-password")
                .enabled(true)
                .build();
    }

    // ---------- updateProfile ----------

    @Test
    void updateProfile_success_mergesAndSaves() {
        userService = service();
        User user = existingUser();
        ProfileUpdateRequest request = ProfileUpdateRequest.builder().firstName("New").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateProfile(request, 1L);

        verify(userMapper).mergeUserInfo(user, request);
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_userNotFound_throwsNotFoundException() {
        userService = service();
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(ProfileUpdateRequest.builder().build(), 2L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(userMapper);
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_success_encodesAndSaves() {
        userService = service();
        User user = existingUser();
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass")
                .newPassword("newPass1!")
                .confirmNewPassword("newPass1!")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.encode("newPass1!")).thenReturn("encoded-new-password");

        userService.changePassword(request, 1L);

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_newPasswordsMismatch_throwsBadRequest() {
        userService = service();
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass")
                .newPassword("newPass1!")
                .confirmNewPassword("different")
                .build();

        assertThatThrownBy(() -> userService.changePassword(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
        verifyNoInteractions(userRepository);
    }

    @Test
    void changePassword_userNotFound_throwsNotFoundException() {
        userService = service();
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass")
                .newPassword("newPass1!")
                .confirmNewPassword("newPass1!")
                .build();
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(request, 3L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadRequest() {
        userService = service();
        User user = existingUser();
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrongPass")
                .newPassword("newPass1!")
                .confirmNewPassword("newPass1!")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "encoded-old-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid current password");
        verify(userRepository, never()).save(any());
    }

    // ---------- deactivateAccount ----------

    @Test
    void deactivateAccount_enabledUser_disablesAndSaves() {
        userService = service();
        User user = existingUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivateAccount(1L);

        assertThat(user.isEnabled()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void deactivateAccount_alreadyDisabled_throwsBadRequest() {
        userService = service();
        User user = existingUser();
        user.setEnabled(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deactivateAccount(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already disabled");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateAccount_userNotFound_throwsNotFoundException() {
        userService = service();
        when(userRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateAccount(4L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- reactivateAccount ----------

    @Test
    void reactivateAccount_disabledUser_enablesAccount() {
        userService = service();
        User user = existingUser();
        user.setEnabled(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.reactivateAccount(1L);

        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void reactivateAccount_alreadyEnabled_throwsBadRequest() {
        userService = service();
        User user = existingUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.reactivateAccount(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already enabled");
    }

    @Test
    void reactivateAccount_userNotFound_throwsNotFoundException() {
        userService = service();
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.reactivateAccount(5L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- deleteAccount ----------

    @Test
    void deleteAccount_doesNotThrowAndDoesNotTouchRepository() {
        userService = service();
        userService.deleteAccount(1L);
        verifyNoInteractions(userRepository);
    }

    // ---------- loadUserByUsername ----------

    @Test
    void loadUserByUsername_found_returnsUser() {
        userService = service();
        User user = existingUser();
        when(userRepository.findByEmailIgnoreCase("adela@gmail.com")).thenReturn(Optional.of(user));

        UserDetails result = userService.loadUserByUsername("adela@gmail.com");

        assertThat(result).isEqualTo(user);
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFoundException() {
        userService = service();
        when(userRepository.findByEmailIgnoreCase("missing@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("missing@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}