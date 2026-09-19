package com.sentinel.aml.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IngestionResultDto {

    private int totalProcessed;
    private int savedCount;
    private int failedCount;
    private int skippedDuplicates;
    private List<String> errors;

    public static IngestionResultDto of(int total, int saved, int failed, int dupes, List<String> errors) {
        return IngestionResultDto.builder()
                .totalProcessed(total)
                .savedCount(saved)
                .failedCount(failed)
                .skippedDuplicates(dupes)
                .errors(errors)
                .build();
    }
}
