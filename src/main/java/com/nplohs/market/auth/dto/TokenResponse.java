package com.nplohs.market.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private UserSummary user;

    @Getter
    @AllArgsConstructor
    public static class UserSummary {
        private Long   id;
        private String email;
        private String nickname;
        private String profileImage;
        private double mannerScore;
    }
}
