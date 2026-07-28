package org.example.amartha.loan.event;

import org.example.amartha.loan.model.Loan;

/**
 * 贷款完成满标事件 — 由 LoanService 在状态转为 INVESTED 后发布。
 * <p>监听器（如 {@code InvestorNotificationListener}）异步消费此事件，
 * 完成邮件通知发送，与主流程解耦。</p>
 */
public record LoanFullyInvestedEvent(Loan loan) {
}
