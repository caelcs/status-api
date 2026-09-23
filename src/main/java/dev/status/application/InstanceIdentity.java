package dev.status.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Unique identity of this running instance, shared by the claim loop and the
 * heartbeat (FR11 §6). Generated once per JVM.
 */
@Component
public class InstanceIdentity {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
