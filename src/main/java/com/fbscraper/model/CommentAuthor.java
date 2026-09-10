package com.fbscraper.model;

/**
 * The limited identity Meta exposes for a person or Page that created a comment.
 * Empty values are expected when Meta withholds profile data.
 */
public record CommentAuthor(
        String id,
        String name,
        String pictureUrl
) {
    public static final CommentAuthor UNKNOWN = new CommentAuthor("", "", "");

    public CommentAuthor {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        pictureUrl = pictureUrl == null ? "" : pictureUrl;
    }

    public boolean isAvailable() {
        return !id.isBlank() || !name.isBlank() || !pictureUrl.isBlank();
    }
}
