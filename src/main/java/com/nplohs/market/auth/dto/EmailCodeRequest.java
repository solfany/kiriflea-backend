package com.nplohs.market.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class EmailCodeRequest {
    @Email
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@nplohs\\.com$", message = "@nplohs.com 이메일만 허용됩니다.")
    private String email;
}
