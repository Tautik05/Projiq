package projiq.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import projiq.common.exception.BadRequestException;

import java.time.Duration;

@Service
public class RateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingService.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimitingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String key, int maxRequests, Duration window) {
        String redisKey = "rate_limit:" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, window);
            }
            return count != null && count <= maxRequests;
        } catch (Exception e) {
            log.warn("Redis rate-limiting failed for key {}, allowing request as fallback: {}", key, e.getMessage());
            return true;
        }
    }

    public void checkRateLimit(String key, int maxRequests, Duration window, String actionName) {
        if (!isAllowed(key, maxRequests, window)) {
            throw new BadRequestException("Rate limit exceeded for " + actionName + ". Please wait before trying again.");
        }
    }
}
