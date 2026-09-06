package com.example.feniksdemo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCaseRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must contain at most 200 characters after trimming") String title) {
    public CreateCaseRequest {
        title = title == null ? null : title.trim();
    }
}
