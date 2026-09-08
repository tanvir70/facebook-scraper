package com.fbscraper.model;

/**
 * Represents a conversation participant (customer or Page).
 */
public record FacebookParticipant(
        String id,
        String name,
        String email
) {
    public FacebookParticipant(String id, String name) {
        this(id, name, null);
    }
}
