package dnd.update;

import java.util.Optional;

/** Test-only bridge to UpdateChecker's package-private helpers. */
public final class UpdateCheckerAccess {
    private UpdateCheckerAccess() { }

    public static Optional<String> newer(String remote, String local) {
        return UpdateChecker.newerThan(remote, local);
    }

    public static String parse(String pom) {
        return UpdateChecker.parsePomVersion(pom);
    }
}
