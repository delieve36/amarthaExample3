package org.example.amartha.loan.email;

/**
 * Email sending abstraction — decouples notification logic from the
 * concrete email delivery mechanism.
 */
public interface EmailService {

    /**
     * Send an agreement letter notification to an investor.
     *
     * @param to             recipient email address
     * @param investorName   display name of the investor
     * @param loanId         the loan identifier
     * @param agreementUrl   the agreement letter URL
     */
    void sendAgreementEmail(String to, String investorName, Long loanId, String agreementUrl);
}
