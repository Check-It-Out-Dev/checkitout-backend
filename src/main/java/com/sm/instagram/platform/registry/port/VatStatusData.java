package com.sm.instagram.platform.registry.port;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO returned by {@link VatRegistryPort} implementations.
 * Contains VAT taxpayer status and registered bank accounts from Biała Lista.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VatStatusData {

    private boolean found;

    /** VAT status: "Czynny" (active), "Zwolniony" (exempt), or null if not registered */
    private String statusVat;

    /** Normalized status for storage */
    private String normalizedStatus;

    /** Registered bank account numbers (IBAN format) */
    private List<String> accountNumbers;

    /** Company name from Biała Lista (for cross-validation with GUS) */
    private String name;

    private String nip;
    private String regon;

    /** Date of VAT registration */
    private String registrationDate;

    /** Date when this data was checked */
    private LocalDate checkedAt;

    /**
     * Normalizes the Polish VAT status string to an enum-compatible value.
     */
    public static String normalizeVatStatus(String statusVat) {
        if (statusVat == null) return "UNREGISTERED";
        return switch (statusVat.trim()) {
            case "Czynny" -> "ACTIVE";
            case "Zwolniony" -> "EXEMPT";
            case "Wyrejestrowany" -> "DEREGISTERED";
            default -> "UNREGISTERED";
        };
    }
}
