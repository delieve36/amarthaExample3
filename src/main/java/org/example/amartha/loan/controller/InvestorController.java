package org.example.amartha.loan.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.dto.CreateInvestorRequest;
import org.example.amartha.loan.model.Investor;
import org.example.amartha.loan.repository.InvestorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/investors")
public class InvestorController {

    private final InvestorRepository investorRepository;

    public InvestorController(InvestorRepository investorRepository) {
        this.investorRepository = investorRepository;
    }

    @PostMapping
    public ResponseEntity<String> createInvestor(@Valid @RequestBody CreateInvestorRequest req) {
        log.info("POST /api/investors — {}", req);
        Investor investor = new Investor();
        investor.setInvestorId(req.getInvestorId());
        investor.setName(req.getName());
        investor.setEmailUrl(req.getEmailUrl());
        investor.setRegisterDate(req.getRegisterDate() != null ? req.getRegisterDate() : java.time.LocalDate.now());
        investorRepository.save(investor);
        return ResponseEntity.status(HttpStatus.CREATED).body("Investor created: " + investor.getInvestorId());
    }
}
