package com.economicbriefing.collector.parser;

import java.util.Date;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;

public record RssItem(
    String title,
    String link,
    String pubDate,
    Date publishedDate,
    String description,
    String content,
    String guid
) {
    public static RssItem fromEntry(SyndEntry entry) {
        String contentValue = null;
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            contentValue = entry.getContents().get(0).getValue();
        }

        String descriptionValue = null;
        SyndContent desc = entry.getDescription();
        if (desc != null) {
            descriptionValue = desc.getValue();
        }

        return new RssItem(
            entry.getTitle(),
            entry.getLink(),
            entry.getPublishedDate() != null ? entry.getPublishedDate().toString() : null,
            entry.getPublishedDate(),
            descriptionValue,
            contentValue,
            entry.getUri()
        );
    }
}
