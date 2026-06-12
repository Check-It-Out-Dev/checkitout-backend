package com.sm.instagram.platform.support.ticket.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a file attachment on a ticket response.
 */
@Entity
@Table(name = "response_attachment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "response_attachment_generator")
    @SequenceGenerator(
        name = "response_attachment_generator",
        sequenceName = "response_attachment_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    /**
     * The response this attachment belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false)
    private TicketResponse response;

    /**
     * Original filename of the uploaded file.
     */
    @NotBlank(message = "File name cannot be blank")
    @Size(max = 255, message = "File name cannot exceed 255 characters")
    private String fileName;

    /**
     * MIME type of the file.
     */
    @NotBlank(message = "Content type cannot be blank")
    @Size(max = 100, message = "Content type cannot exceed 100 characters")
    private String contentType;

    /**
     * URL to the file in Firebase Storage.
     */
    @NotBlank(message = "File URL cannot be blank")
    @Size(max = 2048, message = "File URL cannot exceed 2048 characters")
    @Column(name = "storage_path")
    private String fileUrl;

    /**
     * Size of the file in bytes.
     */
    @Positive(message = "File size must be positive")
    private Long fileSize;

    /**
     * When this attachment was uploaded.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadTime;
}