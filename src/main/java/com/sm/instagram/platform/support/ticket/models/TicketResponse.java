package com.sm.instagram.platform.support.ticket.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a response to a support ticket.
 * Responses can be from the customer or from admin staff.
 */
@Entity
@Table(name = "ticket_response")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_response_generator")
    @SequenceGenerator(
        name = "ticket_response_generator",
        sequenceName = "ticket_response_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    /**
     * The ticket this response belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    /**
     * The content of this response.
     */
    @NotBlank(message = "Content cannot be blank")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Indicates whether this response is from an admin or the customer.
     */
    private boolean isFromAdmin;

    /**
     * Name of the admin who created this response (if applicable).
     */
    private String adminName;

    /**
     * Tracks whether an email notification has been sent for this response.
     */
    private boolean isEmailSent;

    /**
     * When this response was created.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdTime;

    /**
     * Attachments for this response.
     */
    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResponseAttachment> attachments = new ArrayList<>();

    /**
     * Helper method to add an attachment to this response.
     */
    public void addAttachment(ResponseAttachment attachment) {
        attachments.add(attachment);
        attachment.setResponse(this);
    }
}