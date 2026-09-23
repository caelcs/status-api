package dev.status.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Registration request body (api-contract §3.3). Validated at the controller
 * boundary with Jakarta Bean Validation.
 */
public record ServiceRegistration(
        @NotBlank(message = "key is required")
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,63}$", message = "key must match ^[a-z0-9][a-z0-9-]{1,63}$")
        String key,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "env is required")
        @Size(max = 64, message = "env must be at most 64 characters")
        String env,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Size(max = 255, message = "team must be at most 255 characters")
        String team,

        @NotBlank(message = "healthUrl is required")
        @Size(max = 2048, message = "healthUrl must be at most 2048 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "healthUrl must be an absolute http(s) URL")
        String healthUrl,

        List<String> tags
) {
}
