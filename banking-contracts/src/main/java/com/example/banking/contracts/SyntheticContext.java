package com.example.banking.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SyntheticContext(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9-]+") String key,
        boolean employee,
        @Size(max = 30) String cohort,
        @NotBlank @Size(max = 30) String tier,
        @Size(max = 30) String region,
        @NotBlank @Size(max = 40) String channel,
        @NotBlank @Pattern(regexp = "android|ios|web") String platform,
        @NotBlank @Pattern(regexp = "[0-9]+\\.[0-9]+\\.[0-9]+") String appVersion,
        @NotBlank @Size(max = 80) String deviceKey) {}
