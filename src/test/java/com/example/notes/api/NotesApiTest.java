package com.example.notes.api;

import com.example.notes.repository.NoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotesApiTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private NoteRepository repository;

    @BeforeEach
    void clearNotes() {
        repository.findAll().forEach(note -> repository.deleteById(note.id()));
    }

    @Test
    void completeCrudLifecycle() throws Exception {
        JsonNode created = create("""
                {"title":"Plan","content":"Write tests","tags":["work"]}
                """);
        String path = "/notes/" + created.get("id").asText();
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Plan"))
                .andExpect(jsonPath("$.content").value("Write tests"))
                .andExpect(jsonPath("$.tags[0]").value("work"));

        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Changed","content":"Done","tags":["home"]}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.get("id").asText()))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()))
                .andExpect(jsonPath("$.title").value("Changed"))
                .andExpect(jsonPath("$.content").value("Done"))
                .andExpect(jsonPath("$.tags[0]").value("home"));
        mvc.perform(get(path)).andExpect(jsonPath("$.title").value("Changed"));
        mvc.perform(delete(path)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get("/notes")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void filtersByTagAndListsAllNotes() throws Exception {
        create("""
                {"title":"Work","content":"","tags":["work","java"]}
                """);
        create("""
                {"title":"Home","content":"","tags":["home"]}
                """);
        mvc.perform(get("/notes")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/notes").param("tag", "work")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Work"));
        mvc.perform(get("/notes").param("tag", "missing"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/notes").param("tag", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForAllOperationsOnMissingNote() throws Exception {
        String path = "/notes/" + UUID.randomUUID();
        mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Plan\",\"content\":\"Text\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path)).andExpect(status().isNotFound());
        mvc.perform(get("/notes")).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{", "{\"content\":\"Text\"}",
            "{\"title\":\" \u0020\",\"content\":\"Text\"}",
            "{\"title\":\"Plan\"}",
            "{\"title\":\"Plan\",\"content\":\"Text\",\"tags\":[null]}",
            "{\"title\":\"Plan\",\"content\":\"Text\",\"tags\":[\" \u0020\"]}",
            "{\"title\":\"Plan\",\"content\":\"Text\",\"tags\":\"work\"}",
            "{\"title\":\"Plan\",\"content\":\"Text\",\"createdAt\":\"2020-01-01T00:00:00Z\"}"
    })
    void invalidBodiesReturn400AndDoNotCreateNotes(String body) throws Exception {
        mvc.perform(post("/notes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/notes")).andExpect(content().json("[]"));
    }

    @Test
    void invalidUpdateLeavesNoteUnchanged() throws Exception {
        JsonNode created = create("{\"title\":\"Plan\",\"content\":\"Text\"}");
        String path = "/notes/" + created.get("id").asText();
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Plan"));
    }

    @Test
    void missingBodyAndInvalidIdsReturn400() throws Exception {
        mvc.perform(post("/notes").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/notes/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(delete("/notes/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(put("/notes/not-a-uuid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Plan\",\"content\":\"Text\"}"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode create(String body) throws Exception {
        var result = mvc.perform(post("/notes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists()).andExpect(jsonPath("$.createdAt").exists())
                .andReturn();
        JsonNode note = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals("http://localhost/notes/" + note.get("id").asText(),
                result.getResponse().getHeader("Location"));
        return note;
    }
}
