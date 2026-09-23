package dev.status.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the {@link Status} enum as its lowercase wire value in the DB
 * (PRD §4 comment: 'up' | 'degraded' | 'down' | 'unknown').
 */
@Converter
public class StatusConverter implements AttributeConverter<Status, String> {

    @Override
    public String convertToDatabaseColumn(Status status) {
        return status == null ? null : status.value();
    }

    @Override
    public Status convertToEntityAttribute(String dbValue) {
        return Status.fromValue(dbValue);
    }
}
