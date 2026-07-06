package com.nplohs.market.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignUpRequest {

    @Email
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@(nplohs\\.com|krtranslink\\.com)$", message = "회사 이메일 계정으로만 가입 가능합니다.")
    private String email;

    @NotBlank
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
             message = "비밀번호는 영문·숫자·특수문자를 포함해야 합니다.")
    private String password;

    @NotBlank
    @Size(min = 2, max = 20, message = "이름은 2~20자이어야 합니다.")
    private String name;

    @NotBlank
    @Size(min = 2, max = 15, message = "닉네임은 2~15자이어야 합니다.")
    private String nickname;

    private String phoneNumber;     // optional
    private boolean phoneVisible;   // optional
}
