package com.gdsc.skhufp.auth.service;

import com.gdsc.skhufp.auth.domain.entity.Role;
import com.gdsc.skhufp.auth.domain.entity.User;
import com.gdsc.skhufp.auth.dto.TokenDTO;
import com.gdsc.skhufp.auth.dto.UserDTO;
import com.gdsc.skhufp.auth.token.JwtTokenProvider;
import com.gdsc.skhufp.common.exception.auth.*;
import com.gdsc.skhufp.auth.domain.entity.RefreshToken;
import com.gdsc.skhufp.auth.domain.repository.RefreshTokenRepository;
import com.gdsc.skhufp.auth.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(UserDTO userRequest) {
        User user = User.builder()
                .username(userRequest.username())
                .password(passwordEncoder.encode(userRequest.password()))
                .build();

        user.addRole(Role.ROLE_USER);

        userRepository.save(user);
    }

    @Transactional
    public TokenDTO signIn(UserDTO userRequest) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userRequest.username(), userRequest.password());

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRandomRefreshToken();

        RefreshToken refreshTokenInfo = RefreshToken.builder()
                .token(refreshToken)
                .username(userRequest.username())
                .build();

        refreshTokenRepository.save(refreshTokenInfo);

        return TokenDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional(readOnly = true)
    public TokenDTO refresh(TokenDTO tokenRequest) {
        String accessToken = tokenRequest.accessToken();
        String refreshToken = tokenRequest.refreshToken();

        if (!jwtTokenProvider.isValidToken(accessToken, true)) {
            throw new InvalidAccessTokenException();
        } else if (!jwtTokenProvider.isExpiredToken(accessToken, true)) {
            throw new NotExpiredAccessTokenException();
        } else if (!jwtTokenProvider.isValidToken(refreshToken, false)) {
            throw new InvalidRefreshTokenException();
        } else if (jwtTokenProvider.isExpiredToken(refreshToken, false)) {
            throw new ExpiredRefreshTokenException();
        }

        RefreshToken refreshTokenInfo = refreshTokenRepository.findById(refreshToken)
                .orElseThrow(RefreshTokenNotFoundException::new);

        UserDetails userDetails = userRepository.findByUsername(refreshTokenInfo.username())
                .orElseThrow(UserNotFoundException::new);

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);

        return TokenDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
