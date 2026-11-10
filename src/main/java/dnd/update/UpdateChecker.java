package dnd.update;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub for a newer app version by reading the version in pom.xml on the main branch.
 * Every failure (offline, rate limited, odd response) just means "no update info".
 */
public final class UpdateChecker {
    public static final String REPO_URL = "https://github.com/Mykkey/Campaign-Pocketbook";
    static final String POM_URL = "https://raw.githubusercontent.com/Mykkey/Campaign-Pocketbook/main/pom.xml";
    private static final Pattern POM_VERSION =
            Pattern.compile("<artifactId>dnd-campaign-notes</artifactId>\\s*<version>([^<]+)</version>");

    private UpdateChecker() { }

    /** The version this copy was built as, or "0" if unknown. */
    public static String currentVersion() {
        try (InputStream in = UpdateChecker.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("version", "").trim();
                if (!v.isEmpty() && !v.startsWith("${")) return v;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "0";
    }

    /** Blocking; call off the UI thread. Returns the newer version string if GitHub has one. */
    public static Optional<String> findNewerVersion() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(POM_URL))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return Optional.empty();
            return newerThan(parsePomVersion(res.body()), currentVersion());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static String parsePomVersion(String pomXml) {
        Matcher m = POM_VERSION.matcher(pomXml);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Returns {@code remote} if it is a higher version than {@code local}. */
    static Optional<String> newerThan(String remote, String local) {
        if (remote == null || remote.isBlank()) return Optional.empty();
        return compare(remote, local) > 0 ? Optional.of(remote) : Optional.empty();
    }

    /** Compares dotted numeric versions ("1.10.0" > "1.9.2"); non-numeric parts count as 0. */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? num(x[i]) : 0;
            int yi = i < y.length ? num(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
