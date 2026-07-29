package org.example.amartha.loan.repository;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.Investor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Investor profile data access.
 */
@Slf4j
@Repository
public class InvestorRepository {

    private final JdbcTemplate jdbc;
    private final InvestorRowMapper rowMapper;

    public InvestorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.rowMapper = new InvestorRowMapper();
    }

    @Transactional
    public Investor save(Investor investor) {
        String sql = """
            INSERT INTO investors (investor_id, name, email_url, register_date)
            VALUES (?, ?, ?, ?)
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, investor.getInvestorId());
            ps.setString(2, investor.getName());
            ps.setString(3, investor.getEmailUrl());
            ps.setDate(4, investor.getRegisterDate() != null
                ? java.sql.Date.valueOf(investor.getRegisterDate()) : null);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated investor ID");
        }
        investor.setId(key.longValue());
        log.info("Investor saved: id={} investorId={} name={}",
            key.longValue(), investor.getInvestorId(), investor.getName());
        return investor;
    }

    public Optional<Investor> findByInvestorId(Long investorId) {
        String sql = "SELECT * FROM investors WHERE investor_id = ?";
        List<Investor> results = jdbc.query(sql, rowMapper, investorId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Investor> findByInvestorIds(List<Long> investorIds) {
        if (investorIds == null || investorIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(investorIds.size(), "?"));
        String sql = "SELECT * FROM investors WHERE investor_id IN (" + placeholders + ")";
        return jdbc.query(sql, rowMapper, investorIds.toArray());
    }
}
