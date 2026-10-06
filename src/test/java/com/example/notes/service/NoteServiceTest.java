package com.example.notes.service;

import com.example.notes.api.NoteRequest;
import com.example.notes.model.Note;
import com.example.notes.repository.NoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
    @Mock
    private NoteRepository repository;
    private NoteService service;

    @BeforeEach
    void setUp() {
        service = new NoteService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsNoteWithServerAssignedIdAndTime() {
        Note result = service.create(new NoteRequest("Plan", "Write tests", Set.of("work")));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(repository).create(captor.capture());
        assertEquals(result, captor.getValue());
        assertNotNull(result.id());
        assertEquals(NOW, result.createdAt());
        assertEquals("Plan", result.title());
        assertEquals("Write tests", result.content());
        assertEquals(Set.of("work"), result.tags());
    }

    @Test
    void createsNoteWithoutTagsAndWithEmptyContent() {
        Note result = service.create(new NoteRequest("Plan", "", null));
        assertEquals(Set.of(), result.tags());
        assertEquals("", result.content());
    }

    @Test
    void tagsAreDefensivelyCopied() {
        Set<String> tags = new HashSet<>(Set.of("work"));
        Note result = service.create(new NoteRequest("Plan", "", tags));
        tags.add("home");
        assertEquals(Set.of("work"), result.tags());
        assertThrows(UnsupportedOperationException.class, () -> result.tags().add("other"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsInvalidTitleWithoutCallingRepository(String title) {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new NoteRequest(title, "Text", Set.of())));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingContentAndNullRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new NoteRequest("Plan", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(null));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullAndBlankTags() {
        Set<String> tags = new HashSet<>();
        tags.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new NoteRequest("Plan", "Text", tags)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new NoteRequest("Plan", "Text", Set.of(" "))));
        verifyNoInteractions(repository);
    }

    @Test
    void retrievesExistingNote() {
        Note note = note("work");
        when(repository.findById(note.id())).thenReturn(Optional.of(note));
        assertEquals(note, service.get(note.id()));
    }

    @Test
    void missingNoteThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(NoteNotFoundException.class, () -> service.get(id));
    }

    @Test
    void filtersByExactCaseSensitiveTag() {
        Note work = note("work");
        Note home = note("home");
        when(repository.findAll()).thenReturn(List.of(home, work));
        assertEquals(List.of(work), service.list("work"));
        assertTrue(service.list("Work").isEmpty());
        assertTrue(service.list("missing").isEmpty());
    }

    @Test
    void listsAllNotesInStableCreationOrder() {
        Note older = new Note(UUID.randomUUID(), "Old", "", NOW.minusSeconds(1), Set.of());
        Note newer = note("work");
        when(repository.findAll()).thenReturn(List.of(newer, older));
        assertEquals(List.of(older, newer), service.list(null));
    }

    @Test
    void rejectsBlankFilter() {
        assertThrows(IllegalArgumentException.class, () -> service.list(" "));
        verifyNoInteractions(repository);
    }

    @Test
    void updatesFieldsAndPreservesIdAndCreatedAt() {
        Note existing = note("work");
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.replace(any(Note.class))).thenReturn(true);
        Note result = service.update(existing.id(), new NoteRequest("New", "Changed", Set.of("home")));
        assertEquals(existing.id(), result.id());
        assertEquals(existing.createdAt(), result.createdAt());
        assertEquals("New", result.title());
        assertEquals("Changed", result.content());
        assertEquals(Set.of("home"), result.tags());
        verify(repository).replace(result);
    }

    @Test
    void updateOfMissingNoteDoesNotCreateIt() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(NoteNotFoundException.class,
                () -> service.update(id, new NoteRequest("New", "Text", null)));
        verify(repository, never()).replace(any());
        verify(repository, never()).create(any());
    }

    @Test
    void reportsNoteDeletedConcurrentlyDuringUpdate() {
        Note existing = note("work");
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.replace(any(Note.class))).thenReturn(false);
        assertThrows(NoteNotFoundException.class,
                () -> service.update(existing.id(), new NoteRequest("New", "Text", null)));
        verify(repository, never()).create(any());
    }

    @Test
    void invalidUpdateDoesNotTouchRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update(UUID.randomUUID(), new NoteRequest(" ", "Text", null)));
        verifyNoInteractions(repository);
    }

    @Test
    void deletesExistingNote() {
        UUID id = UUID.randomUUID();
        when(repository.deleteById(id)).thenReturn(true);
        assertDoesNotThrow(() -> service.delete(id));
        verify(repository).deleteById(id);
    }

    @Test
    void deletingMissingNoteThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.deleteById(id)).thenReturn(false);
        assertThrows(NoteNotFoundException.class, () -> service.delete(id));
    }

    private static Note note(String tag) {
        return new Note(UUID.randomUUID(), "Plan", "Text", NOW, Set.of(tag));
    }
}
