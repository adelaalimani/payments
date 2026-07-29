package com.adela.payments.service;

import com.adela.payments.entity.User;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.mapper.UserMapper;
import com.adela.payments.repository.UserRepository;
import com.adela.payments.request.ChangePasswordRequest;
import com.adela.payments.request.ProfileUpdateRequest;
import com.adela.payments.user_details.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    public void updateProfile(ProfileUpdateRequest request, Long userId) {
        User savedUser = userRepository.findById(userId).orElseThrow(
                () -> new NotFoundException("User not found with id: " + userId)
        );
        userMapper.mergeUserInfo(savedUser, request);
        userRepository.save(savedUser);
    }

    @Override
    public void changePassword(ChangePasswordRequest request, Long userId) {
        if (!Objects.equals(request.getNewPassword(), request.getConfirmNewPassword())){
            throw new BadRequestException("New passwords do not match");
        }
        User savedUser = userRepository.findById(userId).orElseThrow(
                () -> new NotFoundException("User not found with id: " + userId)
        );
        if (!passwordEncoder.matches(request.getCurrentPassword(), savedUser.getPassword())){
            throw new BadRequestException("Invalid current password");
        }
        savedUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(savedUser);
    }

    @Override
    public void deactivateAccount(Long userId) {
        User savedUser = userRepository.findById(userId).orElseThrow(
                () -> new NotFoundException("User not found with id: " + userId)
        );
        if (!savedUser.isEnabled()) {
            throw new BadRequestException("User is already disabled");
        }
        savedUser.setEnabled(false);
        userRepository.save(savedUser);
    }

    @Override
    public void reactivateAccount(Long userId) {
        User savedUser = userRepository.findById(userId).orElseThrow(
                () -> new NotFoundException("User not found with id: " + userId)
        );
        if (savedUser.isEnabled()) {
            throw new BadRequestException("User is already enabled");
        }
        savedUser.setEnabled(true);

    }

    @Override
    public void deleteAccount(Long userId) {

    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(username).orElseThrow(
                () -> new UsernameNotFoundException("User not found with email: " + username)
        );
    }
}
