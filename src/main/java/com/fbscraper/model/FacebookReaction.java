package com.fbscraper.model;

/**
 * Represents an individual user reaction on a post or comment.
 */
public record FacebookReaction(
        String id,
        String name,
        String type
) {
    public FacebookReaction {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        type = type == null ? "LIKE" : type.toUpperCase();
    }

    public FacebookUser user() {
        return new FacebookUser(id, name);
    }
}
