package com.nplohs.market.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContactRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(max = 2000, message = "문의 내용은 2000자 이하로 작성해주세요.")
    private String content;
}
