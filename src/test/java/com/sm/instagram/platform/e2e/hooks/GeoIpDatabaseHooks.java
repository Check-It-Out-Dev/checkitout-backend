package com.sm.instagram.platform.e2e.hooks;

import com.sm.instagram.platform.common.security.geoip.MaxMindDatabaseService;
import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scenarios that need real geolocation are skipped when there is no GeoLite2 database to look up in.
 *
 * <p>Everything else in the tier runs on a public runner with no credential at all. This is the one
 * exception, and it is not a credential problem: GeoLite2 is a licensed database that MaxMind will only
 * hand over against a licence key, and the file is deliberately not in the repository. On the dev box it
 * sits at {@code ./geoip/GeoLite2-City.mmdb} and these scenarios run for real.
 *
 * <p>Without it every lookup returns nothing, so two IPs on different continents come back at the same
 * place and "travel speed above 500 km/h" reads 0. That is a missing input, not a regression, and a
 * scenario that cannot see its input should say so rather than fail. Cucumber reports the skip with this
 * reason attached; the count is visible in the report.
 *
 * <p>Only {@code @travel-analysis} is gated. The rest of the GeoIP feature - the authorisation
 * boundaries, the validation errors, the shape of the responses - needs no database and keeps running.
 */
@Slf4j
public class GeoIpDatabaseHooks {

    @Autowired
    private MaxMindDatabaseService maxMindDatabaseService;

    @Before(value = "@travel-analysis", order = 1)
    public void requireGeoLite2Database() {
        boolean ready = maxMindDatabaseService != null && maxMindDatabaseService.isDatabaseReady();
        if (!ready) {
            log.info("Skipping: no GeoLite2 database, so every IP resolves to nowhere and travel speed is 0. "
                    + "Provide one at maxmind.database.path, or a licence key at maxmind.license.key.");
        }
        Assumptions.assumeTrue(
                ready,
                "GeoLite2 database not available: travel analysis needs real coordinates for two IPs");
    }
}
