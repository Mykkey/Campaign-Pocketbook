package dnd.storage;

import dnd.model.Project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backs a whole project up to a single .zip file and brings one back (to restore it, move it to another computer
 * or share it). Import never overwrites anything: it always makes a new project folder.
 */
public final class ProjectArchive {
    private ProjectArchive() { }

    private static final String PROJECT_FILE = "project.json";
    /** The most an imported archive may unpack to (a project with a lot of pictures is still far below this). */
    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;

    /** What an import produced. */
    public record Imported(String folderName, int files, long bytes) { }

    /**
     * Writes every file of the project folder to {@code zip}. Files are stored relative to the project folder, so
     * {@code project.json} sits at the top of the archive. Half-written {@code .tmp} files are left out.
     *
     * @return how many files were written
     */
    public static int export(Path projectDir, Path zip) throws IOException {
        if (!Files.isRegularFile(projectDir.resolve(PROJECT_FILE))) throw new IOException("That folder is not a project.");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(projectDir)) {
            s.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .filter(p -> !projectDir.relativize(p).startsWith("trash")) // deleted items stay out of backups and shared copies
                    .forEach(files::add);
        }
        Collections.sort(files);
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Path f : files) {
                String name = projectDir.relativize(f).toString().replace('\\', '/');
                ZipEntry e = new ZipEntry(name);
                e.setTime(Files.getLastModifiedTime(f).toMillis());
                z.putNextEntry(e);
                Files.copy(f, z);
                z.closeEntry();
            }
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
        Files.move(tmp, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return files.size();
    }

    public static Imported importZip(Path zip, Path base) throws IOException {
        return importZip(zip, base, DEFAULT_MAX_BYTES);
    }

    /**
     * Unpacks a project archive into a new folder under {@code base}. The archive may hold the project at its top
     * level or inside one folder. Entries that would land outside the new folder are refused, and so is an archive
     * that unpacks to more than {@code maxBytes}. If anything goes wrong the half-made folder is removed.
     */
    public static Imported importZip(Path zip, Path base, long maxBytes) throws IOException {
        Files.createDirectories(base);
        try (ZipFile z = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            String prefix = findPrefix(z);
            if (prefix == null) throw new IOException("That file does not look like a campaign backup (there is no project.json in it).");

            String wanted = projectName(z, prefix, zip);
            String clean = ProjectStore.sanitize(wanted);
            Path dir = base.resolve(clean);
            int n = 2;
            while (Files.exists(dir)) dir = base.resolve(clean + " (" + n++ + ")");
            Files.createDirectories(dir);
            try {
                int files = 0;
                long bytes = 0;
                int seen = 0;
                java.util.Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (++seen > MAX_ENTRIES) throw new IOException("The archive has too many files.");
                    String name = e.getName().replace('\\', '/');
                    if (!name.startsWith(prefix)) continue;
                    String rel = name.substring(prefix.length());
                    if (rel.isEmpty() || e.isDirectory()) continue;
                    Path target = dir.resolve(rel).normalize();
                    if (!target.startsWith(dir)) throw new IOException("The archive contains an unsafe path: " + e.getName());
                    Files.createDirectories(target.getParent());
                    try (InputStream in = z.getInputStream(e); OutputStream out = Files.newOutputStream(target)) {
                        byte[] buf = new byte[64 * 1024];
                        int r;
                        while ((r = in.read(buf)) > 0) {
                            bytes += r;
                            if (bytes > maxBytes) throw new IOException("The archive is too large to import.");
                            out.write(buf, 0, r);
                        }
                    }
                    files++;
                }
                return new Imported(dir.getFileName().toString(), files, bytes);
            } catch (IOException | RuntimeException ex) {
                deleteQuietly(dir);
                throw ex;
            }
        }
    }

    /** The folder prefix ("" or "Name/") under which project.json sits, or null if there is none. */
    private static String findPrefix(ZipFile z) {
        String best = null;
        java.util.Enumeration<? extends ZipEntry> en = z.entries();
        while (en.hasMoreElements()) {
            String name = en.nextElement().getName().replace('\\', '/');
            if (name.equals(PROJECT_FILE)) return "";
            if (name.endsWith("/" + PROJECT_FILE) && name.indexOf('/') == name.length() - PROJECT_FILE.length() - 1) {
                best = name.substring(0, name.length() - PROJECT_FILE.length());
            }
        }
        return best;
    }

    /** The project's own name if its project.json says one, otherwise the archive's file name. */
    private static String projectName(ZipFile z, String prefix, Path zip) {
        try {
            ZipEntry e = z.getEntry(prefix + PROJECT_FILE);
            try (InputStream in = z.getInputStream(e)) {
                Project p = Json.readStream(in, Project.class);
                if (p != null && p.name != null && !p.name.isBlank()) return p.name;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall back to the file name
        }
        String f = zip.getFileName().toString();
        int dot = f.lastIndexOf('.');
        return dot > 0 ? f.substring(0, dot) : f;
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> all = new ArrayList<>();
            s.forEach(all::add);
            all.sort(java.util.Comparator.reverseOrder());
            for (Path p : all) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
