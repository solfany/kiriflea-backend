package com.nplohs.market.support.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContactRequest {
    private String email;
    private String content;
}
