package com.sm.instagram.platform.subscription.invoicing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FakturowniaInvoiceResponse {

    private Long id;
    private String number;
    private String token;
    private String status;

    @JsonProperty("view_url")
    private String viewUrl;

    @JsonProperty("price_gross")
    private String priceGross;

    @JsonProperty("price_net")
    private String priceNet;

    private String currency;

    @JsonProperty("buyer_name")
    private String buyerName;

    @JsonProperty("buyer_tax_no")
    private String buyerTaxNo;
}
