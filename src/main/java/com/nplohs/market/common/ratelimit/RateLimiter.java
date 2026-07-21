package com.nplohs.market.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis INCR 기반 고정 윈도우 레이트리밋. 채팅 전송/입찰처럼 짧은 시간에 반복 호출되는
 * 엔드포인트가, 자동화된 클라이언트가 초당 수십~수백 번 호출해도 DB/브로드캐스트에
 * 부하를 주지 않도록 앞단에서 막는 용도. 이미 프로젝트 의존성에 있는 Redis만 사용하고,
 * 새 라이브러리(Bucket4j 등)는 추가하지 않았다.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    /** key에 대해 window 시간 동안 maxRequests번까지만 허용한다. 초과하면 false. */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        String redisKey = "ratelimit:" + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            return true; // Redis 장애 시 요청을 막지 않고 통과시킨다 (가용성 우선)
        }
        if (count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count <= maxRequests;
    }

    /** 새 코드를 발급하는 등, 카운터를 다시 처음부터 세야 할 때 초기화한다. */
    public void reset(String key) {
        redisTemplate.delete("ratelimit:" + key);
    }
}
