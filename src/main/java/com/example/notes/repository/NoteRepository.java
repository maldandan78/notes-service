package com.example.notes.repository;

import com.example.notes.model.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository {
    void create(Note note);
    Optional<Note> findById(UUID id);
    List<Note> findAll();
    boolean replace(Note note);
    boolean deleteById(UUID id);
}
