package com.sm.instagram.platform.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

/**
 * Production-ready Redis configuration with support for:
 * - Standalone, Sentinel, and Cluster modes
 * - Connection pooling
 * - Custom serialization
 * - Cache management
 * - Resilience patterns
 */
@Configuration
@EnableCaching
@Slf4j
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfiguration {

    @Value("${spring.data.redis.mode:standalone}")
    private String redisMode;
    
    @Value("${storage.mode:redis}")
    private String storageMode;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration timeout;

    @Value("${spring.data.redis.connect-timeout:10s}")
    private Duration connectTimeout;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    // Sentinel configuration
    @Value("${spring.data.redis.sentinel.master:mymaster}")
    private String sentinelMaster;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private List<String> sentinelNodes;

    // Cluster configuration
    @Value("${spring.data.redis.cluster.nodes:}")
    private List<String> clusterNodes;

    @Value("${spring.data.redis.cluster.max-redirects:3}")
    private int maxRedirects;

    // Connection pool configuration
    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1ms}")
    private Duration maxWait;

    @Value("${spring.data.redis.lettuce.pool.time-between-eviction-runs:60s}")
    private Duration timeBetweenEvictionRuns;

    // Cache configuration
    @Value("${spring.cache.redis.time-to-live:60m}")
    private Duration cacheTimeToLive;

    @Value("${spring.cache.redis.cache-null-values:false}")
    private boolean cacheNullValues;

    @Value("${spring.cache.redis.use-key-prefix:true}")
    private boolean useKeyPrefix;

    @Value("${spring.cache.redis.key-prefix:instagram:}")
    private String keyPrefix;
    
    private LettuceConnectionFactory connectionFactory;
    
    @PostConstruct
    public void init() {
        // Connection test will be performed by RedisStartupConnectivityTest
        log.info("Redis configuration initialized for {} mode with storage.mode={}", redisMode, storageMode);
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection factory in {} mode", redisMode);

        LettuceClientConfiguration clientConfig = buildClientConfiguration();

        switch (redisMode.toLowerCase()) {
            case "sentinel":
                connectionFactory = new LettuceConnectionFactory(sentinelConfiguration(), clientConfig);
                break;
            case "cluster":
                connectionFactory = new LettuceConnectionFactory(clusterConfiguration(), clientConfig);
                break;
            case "standalone":
            default:
                connectionFactory = new LettuceConnectionFactory(standaloneConfiguration(), clientConfig);
                break;
        }
        
        return connectionFactory;
    }

    private RedisStandaloneConfiguration standaloneConfiguration() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(database);
        if (!redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        return config;
    }

    private RedisSentinelConfiguration sentinelConfiguration() {
        RedisSentinelConfiguration config = new RedisSentinelConfiguration();
        config.setMaster(sentinelMaster);
        config.setDatabase(database);
        if (!redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        for (String node : sentinelNodes) {
            String[] parts = node.split(":");
            config.sentinel(parts[0], Integer.parseInt(parts[1]));
        }
        return config;
    }

    private RedisClusterConfiguration clusterConfiguration() {
        RedisClusterConfiguration config = new RedisClusterConfiguration(clusterNodes);
        config.setMaxRedirects(maxRedirects);
        if (!redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        return config;
    }

    private LettuceClientConfiguration buildClientConfiguration() {
        // Socket options
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(connectTimeout)
                .keepAlive(true)
                .tcpNoDelay(true)
                .build();

        // Client options with resilience patterns
        ClientOptions.Builder clientOptionsBuilder = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(TimeoutOptions.enabled(timeout))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .suspendReconnectOnProtocolFailure(false);

        // Additional options for cluster mode
        if ("cluster".equalsIgnoreCase(redisMode)) {
            ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                    .enablePeriodicRefresh(Duration.ofMinutes(1))
                    .enableAllAdaptiveRefreshTriggers()
                    .build();

            clientOptionsBuilder = ClusterClientOptions.builder()
                    .topologyRefreshOptions(topologyRefreshOptions)
                    .socketOptions(socketOptions)
                    .timeoutOptions(TimeoutOptions.enabled(timeout))
                    .autoReconnect(true);
        }

        // Build pooling configuration
        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder poolBuilder = 
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(timeout)
                        .clientOptions(clientOptionsBuilder.build())
                        .poolConfig(buildPoolConfig());

        if (sslEnabled) {
            poolBuilder.useSsl();
        }

        return poolBuilder.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private org.apache.commons.pool2.impl.GenericObjectPoolConfig buildPoolConfig() {
        org.apache.commons.pool2.impl.GenericObjectPoolConfig poolConfig = 
                new org.apache.commons.pool2.impl.GenericObjectPoolConfig();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWait(maxWait);
        poolConfig.setTimeBetweenEvictionRuns(timeBetweenEvictionRuns);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTestOnReturn(false);
        poolConfig.setJmxEnabled(true);
        poolConfig.setJmxNamePrefix("redis-pool");
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }

    /**
     * Creates a copy of the application ObjectMapper with default typing enabled.
     * Default typing embeds {@code @class} metadata in JSON so that Jackson can
     * deserialize back to the original type instead of LinkedHashMap.
     *
     * <p>Without this, {@code @Cacheable} methods returning JPA entities (e.g.
     * {@code CityRepository.findByName()}) would return LinkedHashMap on cache hit.
     */
    private ObjectMapper createRedisObjectMapper(ObjectMapper baseObjectMapper) {
        ObjectMapper redisMapper = baseObjectMapper.copy();

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType("com.sm.instagram.platform.")
                .allowIfSubType("com.sm.instagram.platform.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .build();

        redisMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return redisMapper;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                      ObjectMapper objectMapper) {
        ObjectMapper redisMapper = createRedisObjectMapper(objectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use Jackson serializer for values with type metadata
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setEnableTransactionSupport(false);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectMapper objectMapper) {
        ObjectMapper redisMapper = createRedisObjectMapper(objectMapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheTimeToLive)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(redisMapper)));

        if (!cacheNullValues) {
            config = config.disableCachingNullValues();
        }

        if (useKeyPrefix) {
            config = config.prefixCacheNameWith(keyPrefix);
        }

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    /**
     * Bean for handling Redis list operations with proper serialization
     */
    @Bean
    public RedisTemplate<String, List<Object>> redisListTemplate(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        ObjectMapper redisMapper = createRedisObjectMapper(objectMapper);

        RedisTemplate<String, List<Object>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);

        GenericJackson2JsonRedisSerializer listSerializer =
                new GenericJackson2JsonRedisSerializer(redisMapper);

        template.setValueSerializer(listSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
