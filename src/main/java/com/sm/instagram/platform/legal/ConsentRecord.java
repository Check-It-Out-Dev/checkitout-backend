package com.sm.instagram.platform.legal;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "consent_record")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "consent_record_generator")
    @SequenceGenerator(
            name = "consent_record_generator",
            sequenceName = "consent_record_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    /**
     * Nullable for anonymous cookie consents (cookie banner acceptance before registration).
     * Linked to the user after registration completes.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private LegalDocument document;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "ip_address", columnDefinition = "INET")
    private InetAddress ipAddress;

    @Column(name = "is_trusted")
    private Boolean isTrusted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consent_proof", columnDefinition = "jsonb")
    private ConsentProofPayload consentProof;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private ConsentSource source;
}
