package com.fbscraper.model.facebook;

public record FacebookAttachment(
        String id,
        String mimeType,
        String name,
        Long size,
        String fileUrl,
        String previewUrl
) {
    public FacebookAttachment(String id, String fileUrl) {
        this(id, null, null, null, fileUrl, null);
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public String url() {
        return fileUrl;
    }
}
