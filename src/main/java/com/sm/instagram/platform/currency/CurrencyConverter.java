package com.sm.instagram.platform.currency;

import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

@Component
public class CurrencyConverter {

        private final CurrencyRepository currencyRepository;

        public CurrencyConverter(CurrencyRepository currencyRepository) {
            this.currencyRepository = currencyRepository;
        }

        public Converter<Long, Currency> toCurrencyConverter() {
            // Currency is optional on the entity (nullable FK) — a dto
            // without one is a legal payload, so map null to null instead
            // of letting findById(null) explode into a raw 500.
            return ctx -> ctx.getSource() == null
                    ? null
                    : currencyRepository.findById(ctx.getSource())
                            .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + ctx.getSource()));
        }

        public Converter<Currency, Long> fromCurrencyConverter() {
            return ctx -> {
                Currency currency = ctx.getSource();
                return currency != null ? currency.getId() : null;
            };
        }
}
