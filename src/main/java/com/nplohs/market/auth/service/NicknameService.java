package com.nplohs.market.auth.service;

import com.nplohs.market.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class NicknameService {

    private final UserRepository userRepository;
    private static final Random RANDOM = new Random();

    private static final List<String> ADJECTIVES = List.of(
            "날쌘", "졸린", "용감한", "귀여운", "빠른", "느린", "배고픈", "행복한",
            "슬픈", "화난", "차가운", "따뜻한", "조용한", "시끄러운", "작은", "큰",
            "영리한", "게으른", "부지런한", "신나는"
    );

    private static final List<String> ANIMALS = List.of(
            "고양이", "판다", "토끼", "강아지", "여우", "곰", "사자", "호랑이",
            "원숭이", "코끼리", "기린", "펭귄", "오리", "독수리", "고슴도치", "너구리",
            "비버", "수달", "라쿤", "카피바라"
    );

    public String generate() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String adj    = ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size()));
            String animal = ANIMALS.get(RANDOM.nextInt(ANIMALS.size()));
            String candidate = adj + animal;
            if (!userRepository.existsByNickname(candidate)) return candidate;
        }
        // fallback with numeric suffix
        String base = ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size()))
                    + ANIMALS.get(RANDOM.nextInt(ANIMALS.size()))
                    + (RANDOM.nextInt(900) + 100);
        return base;
    }
}
