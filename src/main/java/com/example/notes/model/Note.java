package com.example.notes.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Note(UUID id, String title, String content, Instant createdAt, Set<String> tags) {
    public Note {
        tags = Set.copyOf(tags);
    }
}
