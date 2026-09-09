package com.fbscraper.model;

public record FacebookAttachment(
        String id,
        String mimeType,
        String name,
        Long size,
        String url,
        String previewUrl
) {
    public boolean isImage() {
        return (mimeType != null && mimeType.toLowerCase().startsWith("image"))
                || (previewUrl != null && !previewUrl.isBlank())
                || (url != null && (url.contains(".png") || url.contains(".jpg") || url.contains(".jpeg") || url.contains(".webp")));
    }
}
