package com.example.notes.api;

import java.util.Set;

/** Client-editable fields; id and createdAt are assigned by the service. */
public record NoteRequest(String title, String content, Set<String> tags) {
}
