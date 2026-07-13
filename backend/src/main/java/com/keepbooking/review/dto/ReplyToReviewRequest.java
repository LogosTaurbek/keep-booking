package com.keepbooking.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplyToReviewRequest {

    @NotBlank
    @Size(max = 2000)
    private String reply;
}
