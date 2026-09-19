package com.sentinel.aml.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorResponseDto {

    private int status;
    private String error;
    private String message;
    private List<String> details;
    private LocalDateTime timestamp;

    public static ErrorResponseDto of(int status, String error, String message) {
        return ErrorResponseDto.builder()
                .status(status)
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponseDto of(int status, String error, String message, List<String> details) {
        return ErrorResponseDto.builder()
                .status(status)
                .error(error)
                .message(message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
