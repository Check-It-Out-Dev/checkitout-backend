package com.sm.instagram.platform.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.common.util.ValidationPatterns;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA entity for a publishing platform (Instagram, TikTok, …).
 *
 * <p>Hibernate / Liquibase contract — must stay in sync:
 * <ul>
 *   <li>Sequence: {@code platform_seq} on schema {@code public},
 *       allocation 50.</li>
 *   <li>Join table: {@code platform_content_type(platform_id, content_type_id)}
 *       with secondary indexes on each side; the index columns are required
 *       because joins fan out heavily on campaign reads.</li>
 *   <li>The reverse side {@code partnershipOpportunities} is mapped by
 *       {@code "platforms"} on {@link PartnershipOpportunity} and is
 *       {@code @JsonIgnore}d to break the cycle on serialization.</li>
 *   <li>{@code socialConnections} is {@code @JsonIgnore}d for the same reason
 *       relative to {@link UserSocialConnection}.</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "platform_generator")
    @SequenceGenerator(
            name = "platform_generator",
            sequenceName = "platform_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @NotBlank(message = "Name cannot be blank")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;

    @Pattern(
            regexp = ValidationPatterns.HTTPS_URL_PATTERN,
            message = "URL must use HTTPS protocol and be properly formatted"
    )
    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    private String logoUrl;

    private Boolean active;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "platform_content_type",
            joinColumns = @JoinColumn(name = "platform_id"),
            inverseJoinColumns = @JoinColumn(name = "content_type_id"),
            indexes = {
                    @Index(columnList = "platform_id"),
                    @Index(columnList = "content_type_id")
            }
    )
    private Set<ContentType> contentTypes = new HashSet<>();

    @ManyToMany(mappedBy = "platforms")
    @JsonIgnore
    private Set<PartnershipOpportunity> partnershipOpportunities = new HashSet<>();

    @OneToMany(mappedBy = "platform")
    @JsonIgnore
    private List<UserSocialConnection> socialConnections;
}
