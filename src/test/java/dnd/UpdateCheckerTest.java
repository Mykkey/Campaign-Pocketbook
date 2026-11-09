package dnd;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    // UpdateChecker's helpers are package-private in dnd.update, so test through a same-package shim.
    @Test
    void versionComparison() throws Exception {
        assertEquals(Optional.of("1.2.0"), dnd.update.UpdateCheckerAccess.newer("1.2.0", "1.1.0"));
        assertEquals(Optional.of("1.10.0"), dnd.update.UpdateCheckerAccess.newer("1.10.0", "1.9.2"));
        assertEquals(Optional.empty(), dnd.update.UpdateCheckerAccess.newer("1.1.0", "1.1.0"));
        assertEquals(Optional.empty(), dnd.update.UpdateCheckerAccess.newer("1.0.0", "1.1.0"));
        assertEquals(Optional.empty(), dnd.update.UpdateCheckerAccess.newer(null, "1.1.0"));
    }

    @Test
    void parsesVersionOfThisProjectNotItsDependencies() {
        String pom = "<project><groupId>dnd</groupId><artifactId>dnd-campaign-notes</artifactId>"
                + "<version>2.3.4</version><dependencies><dependency><artifactId>x</artifactId>"
                + "<version>9.9.9</version></dependency></dependencies></project>";
        assertEquals("2.3.4", dnd.update.UpdateCheckerAccess.parse(pom));
        assertNull(dnd.update.UpdateCheckerAccess.parse("<project/>"));
    }
}
