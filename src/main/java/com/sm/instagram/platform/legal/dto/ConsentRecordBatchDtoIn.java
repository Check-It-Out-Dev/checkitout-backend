package com.sm.instagram.platform.legal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ConsentRecordBatchDtoIn {

    /**
     * The element annotation is the load-bearing part. {@code @Valid} on the list cascades into
     * each element, but a null element has nothing to cascade into, so `{"records": [null]}` passed
     * the boundary and the service dereferenced it: a 500 for input a caller typed.
     */
    @NotEmpty(message = "{validation.consent.records.required}")
    private List<@NotNull(message = "{validation.consent.record.required}") @Valid ConsentRecordDtoIn> records;
}
