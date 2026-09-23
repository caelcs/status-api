package dev.status.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Registration request body (api-contract §3.3).
 */
public record ServiceRegistration(
        @NotBlank(message = "key is required")
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,63}$", message = "key must match ^[a-z0-9][a-z0-9-]{1,63}$")
        String key,

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "env is required")
        String env,

        String description,

        String team,

        @NotBlank(message = "healthUrl is required")
        @Pattern(regexp = "^https?://.+", message = "healthUrl must be an absolute http(s) URL")
        String healthUrl,

        List<String> tags
) {
}
