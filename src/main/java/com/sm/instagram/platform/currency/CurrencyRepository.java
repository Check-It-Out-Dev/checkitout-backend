package com.sm.instagram.platform.currency;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CurrencyRepository extends BaseRepository<Currency, Long> {
    Optional<Currency> findByIsoCode(String isoCode);
}
