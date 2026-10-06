package com.example.notes.service;

import com.example.notes.api.NoteRequest;
import com.example.notes.model.Note;
import com.example.notes.repository.NoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NoteService {
    private final NoteRepository repository;
    private final Clock clock;

    public NoteService(NoteRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Note create(NoteRequest request) {
        validate(request);
        Note note = new Note(UUID.randomUUID(), request.title(), request.content(),
                Instant.now(clock), tagsOf(request));
        repository.create(note);
        return note;
    }

    public Note get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
    }

    public List<Note> list(String tag) {
        if (tag != null && tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        return repository.findAll().stream()
                .filter(note -> tag == null || note.tags().contains(tag))
                .sorted(Comparator.comparing(Note::createdAt).thenComparing(Note::id))
                .toList();
    }

    public Note update(UUID id, NoteRequest request) {
        validate(request);
        Note existing = get(id);
        Note updated = new Note(id, request.title(), request.content(),
                existing.createdAt(), tagsOf(request));
        if (!repository.replace(updated)) {
            throw new NoteNotFoundException(id);
        }
        return updated;
    }

    public void delete(UUID id) {
        if (!repository.deleteById(id)) {
            throw new NoteNotFoundException(id);
        }
    }

    private static Set<String> tagsOf(NoteRequest request) {
        return request.tags() == null ? Set.of() : request.tags();
    }

    private static void validate(NoteRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title is required and must not be blank");
        }
        if (request.content() == null) {
            throw new IllegalArgumentException("content is required (an empty string is allowed)");
        }
        if (tagsOf(request).stream().anyMatch(tag -> tag == null || tag.isBlank())) {
            throw new IllegalArgumentException("tags must contain only non-blank strings");
        }
    }
}
