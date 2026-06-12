package com.sm.instagram.platform.subscription.stripe;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    private String secretKey;
    private String publicKey;
    private String webhookSecret;
    private Prices prices = new Prices();

    @Getter
    @Setter
    public static class Prices {
        private String business;
        private String enterprise;
    }
}
