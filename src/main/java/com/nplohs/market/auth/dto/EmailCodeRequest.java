package com.nplohs.market.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class EmailCodeRequest {
    @Email
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@(nplohs\\.com|krtranslink\\.com)$", message = "회사 이메일 계정으로만 가입 가능합니다.")
    private String email;
}
