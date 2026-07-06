package com.nplohs.market.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class EmailVerifyRequest {
    @Email @NotBlank
    private String email;

    @NotBlank
    @Size(min = 6, max = 6, message = "인증코드는 6자리입니다.")
    private String code;
}
