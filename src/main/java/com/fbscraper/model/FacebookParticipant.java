package com.fbscraper.model;

public record FacebookParticipant(
        String id,
        String name,
        String email
) {
    public FacebookParticipant(String id, String name) {
        this(id, name, null);
    }
}
