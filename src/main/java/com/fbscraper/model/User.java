package com.fbscraper.model;

public record User(
        String id,
        String name,
        String email,
        String pictureUrl
) {
    public static final User ANONYMOUS = new User("", "Anonymous", null, null);

    public User {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        pictureUrl = pictureUrl == null || pictureUrl.isBlank() ? null : pictureUrl;
    }

    public User(String id, String name) {
        this(id, name, null, null);
    }

    public User(String id, String name, String email) {
        this(id, name, email, null);
    }

    public boolean hasId() {
        return !id.isBlank();
    }

    public boolean hasName() {
        return !name.isBlank();
    }

    public boolean hasPicture() {
        return pictureUrl != null;
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
