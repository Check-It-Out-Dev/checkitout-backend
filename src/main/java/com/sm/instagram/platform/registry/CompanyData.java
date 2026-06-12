package com.sm.instagram.platform.registry;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_data")
public class CompanyData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company_data_generator")
    @SequenceGenerator(
            name = "company_data_generator",
            sequenceName = "company_data_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "nip", nullable = false, unique = true, length = 10)
    private String nip;

    @Column(name = "regon", length = 14)
    private String regon;

    @Column(name = "krs", length = 10)
    private String krs;

    @Column(name = "company_name", nullable = false, length = 500)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false, length = 30)
    private CompanyType companyType;

    @Column(name = "legal_form_code", length = 10)
    private String legalFormCode;

    @Column(name = "legal_form_name", length = 255)
    private String legalFormName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registered_address", columnDefinition = "jsonb")
    private Map<String, String> registeredAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correspondence_address", columnDefinition = "jsonb")
    private Map<String, String> correspondenceAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pkd_codes", columnDefinition = "jsonb")
    private List<Map<String, Object>> pkdCodes;

    @Column(name = "vat_status", length = 30)
    private String vatStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_accounts", columnDefinition = "jsonb")
    private List<String> bankAccounts;

    @Column(name = "owner_name", length = 255)
    private String ownerName;

    @Column(name = "registry_data_fetched_at")
    private LocalDateTime registryDataFetchedAt;

    @Column(name = "data_verified", nullable = false)
    private Boolean dataVerified = false;

    @Column(name = "source_gus", nullable = false)
    private Boolean sourceGus = false;

    @Column(name = "source_vat", nullable = false)
    private Boolean sourceVat = false;

    @Column(name = "source_ceidg", nullable = false)
    private Boolean sourceCeidg = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_gus_response", columnDefinition = "jsonb")
    private Map<String, Object> rawGusResponse;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "last_update_time", nullable = false)
    private LocalDateTime lastUpdateTime;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
