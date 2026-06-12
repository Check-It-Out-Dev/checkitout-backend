package com.sm.instagram.platform.legal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ConsentRecordBatchDtoIn {

    @NotEmpty(message = "{validation.consent.records.required}")
    @Valid
    private List<ConsentRecordDtoIn> records;
}
