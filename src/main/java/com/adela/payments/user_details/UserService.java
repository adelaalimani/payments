package com.adela.payments.user_details;

import com.adela.payments.request.ChangePasswordRequest;
import com.adela.payments.request.ProfileUpdateRequest;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {

    void updateProfile(ProfileUpdateRequest request, Long userId);
    void changePassword(ChangePasswordRequest request, Long userId);
    void deactivateAccount(Long userId);
    void reactivateAccount(Long userId);
    void deleteAccount(Long userId);
}
