package de.medo.holonetbridge.commons.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TicketProviderConverter implements AttributeConverter<TicketProvider, String> {

    @Override
    public String convertToDatabaseColumn(TicketProvider provider) {
        return provider == null ? null : provider.getId();
    }

    @Override
    public TicketProvider convertToEntityAttribute(String id) {
        return id == null ? null : TicketProvider.fromId(id);
    }
}
