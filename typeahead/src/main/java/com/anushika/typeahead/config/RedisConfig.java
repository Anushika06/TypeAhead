package com.anushika.typeahead.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure configuration.
 *
 * <p>Exposes two templates:
 * <ul>
 *   <li>{@link StringRedisTemplate} — the primary template used by
 *       {@code SuggestionCacheService} for all ZSET operations.
 *       Keys and members are UTF-8 strings; scores are native Redis
 *       doubles, so <em>no JSON serialisation is needed</em> for the
 *       Sorted Set data model.</li>
 *   <li>{@link RedisTemplate}{@code <String, Object>} — kept for
 *       future phases that may need to store structured values.</li>
 * </ul>
 *
 * <p>The underlying {@link RedisConnectionFactory} (Lettuce by default)
 * is auto-configured by Spring Boot from the {@code spring.data.redis.*}
 * properties in {@code application.yaml}.
 *
 * <p>No caching logic lives here — this class only establishes the
 * low-level connectivity and serialisation contract.
 */
@Configuration
public class RedisConfig {

    /**
     * Primary template for all ZSET (Sorted Set) cache operations.
     *
     * <p>{@link StringRedisTemplate} is a convenience sub-class of
     * {@link RedisTemplate} that pre-configures {@link StringRedisSerializer}
     * for both keys and values.  Since ZSET members are plain query strings
     * and scores are native Redis doubles, this is the correct and
     * minimal serialisation choice.
     *
     * @param connectionFactory auto-configured Lettuce connection factory
     * @return configured string template
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Generic template retained for future use (e.g. storing structured
     * objects in a later phase).
     *
     * @param connectionFactory auto-configured Lettuce connection factory
     * @return configured generic template
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}


