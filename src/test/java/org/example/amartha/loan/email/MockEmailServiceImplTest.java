package org.example.amartha.loan.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmailServiceImpl")
class MockEmailServiceImplTest {

    private Path tempLogFile;
    private MockEmailServiceImpl emailService;

    @BeforeEach
    void setUp() throws IOException {
        tempLogFile = Files.createTempFile("email-test-", ".log");
        emailService = new MockEmailServiceImpl(tempLogFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempLogFile);
    }

    @Test
    @DisplayName("sendAgreementEmail → writes formatted entry to email.log")
    void sendAgreementEmail_shouldWriteEntry() throws IOException {
        emailService.sendAgreementEmail("alice@example.com", "Alice Wang", 1L,
            "http://localhost:8080/api/loans/agreement/1");

        List<String> lines = Files.readAllLines(tempLogFile, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());

        String line = lines.get(0);
        assertTrue(line.startsWith("["), "should start with ISO timestamp bracket");
        assertTrue(line.contains("TO=alice@example.com"), "should contain TO");
        assertTrue(line.contains("INVESTOR=Alice Wang"), "should contain INVESTOR");
        assertTrue(line.contains("LOAN=1"), "should contain LOAN");
        assertTrue(line.contains("AGREEMENT=http://localhost:8080/api/loans/agreement/1"),
            "should contain AGREEMENT URL");
    }

    @Test
    @DisplayName("sendAgreementEmail → appends multiple entries")
    void sendAgreementEmail_shouldAppend() throws IOException {
        emailService.sendAgreementEmail("a@x.com", "A", 1L, "http://u1");
        emailService.sendAgreementEmail("b@x.com", "B", 1L, "http://u1");
        emailService.sendAgreementEmail("c@x.com", "C", 2L, "http://u2");

        List<String> lines = Files.readAllLines(tempLogFile, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).contains("INVESTOR=B"));
        assertTrue(lines.get(2).contains("LOAN=2"));
    }

    @Test
    @DisplayName("sendAgreementEmail → creates parent directories if needed")
    void sendAgreementEmail_shouldCreateParentDirs() throws IOException {
        Path nested = tempLogFile.resolveSibling("subdir").resolve("nested-email.log");
        var svc = new MockEmailServiceImpl(nested.toString());

        svc.sendAgreementEmail("x@x.com", "X", 1L, "http://x");

        assertTrue(Files.exists(nested));
        Files.deleteIfExists(nested);
        Files.deleteIfExists(nested.getParent());
    }

    @Test
    @DisplayName("sanitize CR/LF in email → log-forgery prevented")
    void sendAgreementEmail_sanitizesCRLF() throws IOException {
        emailService.sendAgreementEmail("alice@example.com\r\nEVIL=injected", "Bob\nSmith", 1L,
            "http://u");

        List<String> lines = Files.readAllLines(tempLogFile, StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), "CR/LF injection must not produce extra lines");

        String line = lines.get(0);
        assertTrue(line.contains("TO=alice@example.com\\r\\nEVIL=injected"),
            "CRLF in email should be escaped, not interpreted");
        assertTrue(line.contains("INVESTOR=Bob\\nSmith"),
            "LF in name should be escaped, not interpreted");
    }

    @Test
    @DisplayName("sanitize null email/name → writes 'null' safely")
    void sendAgreementEmail_nullFields() throws IOException {
        emailService.sendAgreementEmail(null, null, 1L, "http://u");

        List<String> lines = Files.readAllLines(tempLogFile, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("TO=null"));
        assertTrue(line.contains("INVESTOR=null"));
    }
}
