package dnd.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dnd.model.BoardObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() { }

    /** Compact JSON (used for undo snapshots and the clipboard). */
    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T readStream(InputStream in, Class<T> type) throws IOException {
        return MAPPER.readValue(in, type);
    }

    public static List<BoardObject> readObjects(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<BoardObject>>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<dnd.model.WorldStamp> readStamps(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<dnd.model.WorldStamp>>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Pretty-printed, written atomically-ish (temp file then move) so a crash can't corrupt a board. */
    public static void writeFile(Path file, Object o) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), o);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static <T> T readFile(Path file, Class<T> type) throws IOException {
        return MAPPER.readValue(file.toFile(), type);
    }
}
