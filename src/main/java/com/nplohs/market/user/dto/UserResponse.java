package com.nplohs.market.user.dto;

import com.nplohs.market.user.entity.User;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class UserResponse {
    private final Long id;
    private final String email;
    private final String nickname;
    private final String profileImage;
    private final String createdAt;
    private final double mannerScore;
    private final int listingCount;

    public UserResponse(User user, int listingCount) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.profileImage = user.getProfileImage();
        this.mannerScore = user.getMannerScore();
        this.listingCount = listingCount;
        this.createdAt = user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
