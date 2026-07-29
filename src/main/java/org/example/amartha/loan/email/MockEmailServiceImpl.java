package org.example.amartha.loan.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mock email implementation — writes to {@code email.log} instead of
 * connecting to a real SMTP server.
 *
 * <p>In production, replace with a {@code SmtpEmailServiceImpl} that
 * uses {@code JavaMailSender}.</p>
 */
@Slf4j
@Service
public class MockEmailServiceImpl implements EmailService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final Path logPath;

    public MockEmailServiceImpl(@Value("${app.notification.email-log-path:email.log}") String logPath) {
        this.logPath = Paths.get(logPath).toAbsolutePath();
        log.info("MockEmailService initialized — emails will be written to {}", this.logPath);
    }

    /**
     * Strip CR/LF to prevent log-forgery via user-controlled fields.
     */
    private static String sanitize(String s) {
        if (s == null) return "null";
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }

    @Override
    public void sendAgreementEmail(String to, String investorName, Long loanId, String agreementUrl) {
        String entry = String.format("[%s] TO=%s | INVESTOR=%s | LOAN=%d | AGREEMENT=%s%n",
            FMT.format(OffsetDateTime.now()), sanitize(to), sanitize(investorName), loanId, agreementUrl);

        try {
            Files.createDirectories(logPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(logPath,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(entry);
            }
            log.info("[MOCK EMAIL] Sent agreement notification to {} (investor={}, loan={})",
                to, investorName, loanId);
        } catch (IOException e) {
            log.error("Failed to write email log entry: {}", e.getMessage(), e);
            throw new RuntimeException("Mock email write failed", e);
        }
    }
}
