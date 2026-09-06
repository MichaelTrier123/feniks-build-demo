package com.example.feniksdemo;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull(message = "status must be provided") CaseStatus status) {
}
