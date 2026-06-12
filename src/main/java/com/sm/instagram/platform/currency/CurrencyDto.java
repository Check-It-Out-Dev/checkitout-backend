package com.sm.instagram.platform.currency;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrencyDto {
    private Long id;
    private String name;
    private String isoCode;
    private String sign;
    private String countryCode;

}
