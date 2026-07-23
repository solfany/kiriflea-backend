package com.nplohs.market.me.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/me/profile 요청 바디.
 * 필드가 null이면 해당 항목은 변경하지 않는 부분 수정(partial update)이므로 @NotBlank는 걸지 않고,
 * 값이 있을 때만 User 엔티티 컬럼 길이(name=20, nickname=15, profileImage=255)에 맞춰 검증한다.
 */
public record ProfileUpdateRequest(
        @Size(max = 20, message = "이름은 20자 이하여야 합니다.") String name,
        @Size(max = 15, message = "닉네임은 15자 이하여야 합니다.") String nickname,
        @Size(max = 255, message = "프로필 이미지 주소가 너무 깁니다.")
        String profileImage
) {}
