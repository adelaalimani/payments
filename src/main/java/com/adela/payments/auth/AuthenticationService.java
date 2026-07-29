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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    public AuthenticationService(AuthenticationManager authenticationManager, JwtService jwtService, UserRepository userRepository, RoleRepository roleRepository, UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
    }

    public AuthenticationResponse login(AuthenticationRequest request){
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        User user = (User) authentication.getPrincipal();
        String token = jwtService.generateAccessToken(user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());
        String tokenType = "Bearer";
        return new AuthenticationResponse(token, refreshToken, tokenType);
    }

    @Transactional
    public void register(RegistrationRequest request){
        checkUserEmail(request.getEmail());
        checkUserPhoneNumber(request.getPhoneNumber());
        checkPasswords(request.getPassword(), request.getConfirmPassword());

        Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow(
                () -> new BadRequestException("Role 'ROLE_USER' not found")
        );
        List<Role> roles = List.of(userRole);
        User user = userMapper.toUser(request);
        user.setRoles(roles);
        log.info("Registering user: {}", user);
        userRepository.save(user);
    }

    public AuthenticationResponse refreshToken(RefreshRequest request){
        String accessToken = jwtService.refreshAccessToken(request.getRefreshToken());
        return new AuthenticationResponse(accessToken, request.getRefreshToken(), "Bearer");
    }

    private void checkPasswords(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
    }

    private void checkUserPhoneNumber(String phoneNumber) {
        boolean userExists = userRepository.existsByPhoneNumber(phoneNumber);
        if (userExists) {
            throw new BadRequestException("User with phone number " + phoneNumber + " already exists");
        }
    }

    private void checkUserEmail(String email) {
        boolean userExists = userRepository.existsByEmailIgnoreCase(email);
        if (userExists) {
            throw new BadRequestException("User with email " + email + " already exists");
        }
    }


}
