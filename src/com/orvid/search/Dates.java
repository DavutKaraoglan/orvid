package com.orvid.search;

import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class Dates {

    private Dates() {
    }

    public static void set(StreamInfoItem item, String text) {
        if (text == null || text.isEmpty()) return;
        String iso = text.trim().replace(' ', 'T');
        try {
            Instant at = iso.endsWith("Z") || iso.matches(".*[+-]\\d\\d:?\\d\\d$")
                    ? OffsetDateTime.parse(iso).toInstant()
                    : LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
            item.setUploadDate(new DateWrapper(at, false));
        } catch (DateTimeParseException ignored) {
        }
    }

    public static void set(StreamInfoItem item, long epochSeconds) {
        if (epochSeconds > 0) item.setUploadDate(new DateWrapper(Instant.ofEpochSecond(epochSeconds), false));
    }
}
