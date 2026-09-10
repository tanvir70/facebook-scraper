package com.fbscraper.model.facebook;

public record FacebookReaction(String id, String name, String type) {
    public FacebookReaction {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        type = type == null || type.isBlank() ? "LIKE" : type.toUpperCase();
    }

    public FacebookUser user() {
        return new FacebookUser(id, name);
    }
}
