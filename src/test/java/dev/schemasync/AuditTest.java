package dev.schemasync;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditTest {
    private Audit.Report audit(String rows) throws IOException {
        var mapping = Audit.JSON.readTree(Files.readString(Path.of("fixtures/mapping.json")));
        return Audit.compare(new BufferedReader(new StringReader(rows)), mapping);
    }

    @Test void syntheticCatalog() throws IOException {
        var report = audit(Files.readString(Path.of("fixtures/catalog.jsonl")));
        assertEquals(3, report.documents());
        var problems = report.fields().stream().filter(f -> !f.findings().isEmpty()).toList();
        assertEquals(List.of("aisle", "quantity"), problems.stream().map(Audit.Field::path).toList());
        assertEquals(List.of("unmapped"), problems.getFirst().findings());
        assertEquals(List.of("mixed_types", "mapping_conflict"), problems.getLast().findings());
        assertNotNull(problems.getLast().suggestion());
        assertTrue(report.fields().stream().anyMatch(f -> f.path().equals("details.active")));
    }

    @Test void overflowConflictsWithLong() throws IOException {
        assertEquals(List.of("mapping_conflict"), audit("{\"quantity\":9223372036854775808}").fields().getFirst().findings());
    }

    @Test void nullsAndEmptyArraysAreInconclusive() throws IOException {
        assertTrue(audit("{\"quantity\":[null,[]]}").fields().getFirst().findings().isEmpty());
    }

    @Test void rejectsNonDocuments() {
        assertThrows(IOException.class, () -> audit("[]"));
        assertThrows(IOException.class, () -> audit("{} {}"));
    }
}
