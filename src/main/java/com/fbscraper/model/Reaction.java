package com.fbscraper.model;

public record Reaction(
        String id,
        String name,
        String type
) {
    public Reaction {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        type = type == null ? "LIKE" : type.toUpperCase();
    }

    public User user() {
        return new User(id, name);
    }
}
