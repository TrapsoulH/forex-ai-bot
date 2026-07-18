package com.forexbot.service;

import com.forexbot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Handles Google OAuth2 login.
 * On first login, creates a USER account automatically from the Google profile.
 * On subsequent logins, loads the existing account.
 */
@Slf4j
@Service
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserService userService;

    public OAuth2UserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(request);

        String email    = oauthUser.getAttribute("email");
        String fullName = oauthUser.getAttribute("name");

        log.info("OAuth2 login attempt: email={}", email);

        User user = userService.findOrCreateOAuthUser(email, fullName);

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                Map.of(
                        "email",    email,
                        "name",     fullName != null ? fullName : email,
                        "username", user.getUsername()
                ),
                "username"
        );
    }
}
