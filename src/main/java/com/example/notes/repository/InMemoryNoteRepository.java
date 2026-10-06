package com.example.notes.repository;

import com.example.notes.model.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryNoteRepository implements NoteRepository {
    private final ConcurrentMap<UUID, Note> notes = new ConcurrentHashMap<>();

    @Override
    public void create(Note note) {
        if (notes.putIfAbsent(note.id(), note) != null) {
            throw new IllegalStateException("Duplicate note id: " + note.id());
        }
    }

    @Override
    public Optional<Note> findById(UUID id) {
        return Optional.ofNullable(notes.get(id));
    }

    @Override
    public List<Note> findAll() {
        return List.copyOf(notes.values());
    }

    @Override
    public boolean replace(Note note) {
        // Atomic replacement prevents a concurrent delete from resurrecting a note.
        return notes.replace(note.id(), note) != null;
    }

    @Override
    public boolean deleteById(UUID id) {
        return notes.remove(id) != null;
    }
}
