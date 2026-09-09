package com.fbscraper.model;

public record FacebookUser(
        String id,
        String name,
        String email
) {
    public static final FacebookUser ANONYMOUS = new FacebookUser("", "Anonymous", null);

    public FacebookUser {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
    }

    public FacebookUser(String id, String name) {
        this(id, name, null);
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
