package com.sm.instagram.platform.subscription.invoicing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fakturownia")
public class FakturowniaProperties {

    private boolean enabled = true;
    private String apiKey;
    private String domain = "checkitout";
    private int departmentId = 1878648;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;

    // Seller info (from department, but hardcoded for simplicity)
    private String sellerName = "CHECK IT OUT SP. Z O.O.";
    private String sellerTaxNo = "8943264018";
    private String exemptTaxKind = "art113";

    public String getBaseUrl() {
        return "https://" + domain + ".fakturownia.pl";
    }

    /**
     * Operational readiness: the flag is on AND an API key is present.
     * The api-key env var defaults to empty so a box without Fakturownia
     * still boots — an unconfigured adapter must behave exactly like a
     * disabled one (fail the send, let the retry cron hold the queue).
     */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
