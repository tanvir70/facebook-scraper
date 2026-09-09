package com.fbscraper.model;

public record User(
        String id,
        String name,
        String email
) {
    public static final User ANONYMOUS = new User("", "Anonymous", null);

    public User {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
    }

    public User(String id, String name) {
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
