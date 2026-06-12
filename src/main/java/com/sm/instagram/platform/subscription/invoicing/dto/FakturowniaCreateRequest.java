package com.sm.instagram.platform.subscription.invoicing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FakturowniaCreateRequest {

    @JsonProperty("api_token")
    private String apiToken;

    @JsonProperty("invoice")
    private InvoiceData invoice;

    @Data
    @Builder
    public static class InvoiceData {

        private String kind;

        @JsonProperty("department_id")
        private Integer departmentId;

        @JsonProperty("sell_date")
        private String sellDate;

        @JsonProperty("issue_date")
        private String issueDate;

        @JsonProperty("payment_to")
        private String paymentTo;

        @JsonProperty("payment_type")
        private String paymentType;

        @JsonProperty("seller_name")
        private String sellerName;

        @JsonProperty("seller_tax_no")
        private String sellerTaxNo;

        @JsonProperty("buyer_name")
        private String buyerName;

        @JsonProperty("buyer_tax_no")
        private String buyerTaxNo;

        @JsonProperty("buyer_company")
        private Boolean buyerCompany;

        @JsonProperty("buyer_street")
        private String buyerStreet;

        @JsonProperty("buyer_city")
        private String buyerCity;

        @JsonProperty("buyer_post_code")
        private String buyerPostCode;

        @JsonProperty("buyer_country")
        private String buyerCountry;

        @JsonProperty("exempt_tax_kind")
        private String exemptTaxKind;

        private String currency;
        private String lang;
        private String oid;

        @JsonProperty("oid_unique")
        private String oidUnique;

        private List<Position> positions;
    }

    @Data
    @Builder
    public static class Position {
        private String name;
        private Integer quantity;
        private String tax;

        @JsonProperty("total_price_gross")
        private BigDecimal totalPriceGross;
    }
}
