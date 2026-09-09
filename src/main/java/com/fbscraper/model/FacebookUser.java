package com.fbscraper.model;

/**
 * Represents a Facebook user interacting with the Page (commenter, replier, reactor, reviewer).
 */
public record FacebookUser(
        String id,
        String name
) {
    public static final FacebookUser ANONYMOUS = new FacebookUser("", "Anonymous");

    public FacebookUser {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
    }

    public boolean hasId() {
        return !id.isBlank();
    }

    public boolean hasName() {
        return !name.isBlank();
    }

    public String displayName() {
        if (hasName()) {
            return name;
        }
        if (hasId()) {
            return "User " + id;
        }
        return "Anonymous";
    }
}
