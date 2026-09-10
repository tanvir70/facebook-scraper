package com.fbscraper.model.instagram;

public record InstagramUser(String id, String username, String name, String pictureUrl) {
    public static final InstagramUser ANONYMOUS = new InstagramUser("", "Anonymous", "Anonymous", null);

    public InstagramUser(String id, String username) {
        this(id, username, username, null);
    }

    public InstagramUser(String id, String username, String name) {
        this(id, username, name, null);
    }

    public InstagramUser {
        id = id == null ? "" : id;
        username = username == null || username.isBlank() ? "Instagram User" : username;
        name = name == null || name.isBlank() ? username : name;
        pictureUrl = pictureUrl == null || pictureUrl.isBlank() ? null : pictureUrl;
    }

    public boolean hasPicture() {
        return pictureUrl != null;
    }
}
