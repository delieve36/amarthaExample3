package org.example.amartha.loan.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 贷款 — 领域模型的核心实体。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class Loan {

    /** Unique Id **/
    private Long id;

    /** 记录创建时间（服务器时区） */
    private LocalDateTime gmtCreate;

    /** 记录修改时间（服务器时区） */
    private LocalDateTime gmtModify;

    /** 借款人Id **/
    private Long borrowerId;

    /** 借款人姓名（非必填） */
    private String borrowerName;

    /** 本金金额（货币最小单位，如 1 USD = 100） */
    private BigDecimal principalAmount;

    /** 年化利率（如 10 表示 10%） */
    private BigDecimal interestRate;

    /** 投资回报率（如 8 表示 8%） */
    private BigDecimal roi;

    /** 币种（如 CNY、IDR、USD） */
    private String currency;

    /** 当前状态 */
    private LoanStateEnum currState;

    /** 创建时间（含时区） */
    private OffsetDateTime initDatetime;

    /** 协议信发送时间（含时区，投资完成后设置） */
    private OffsetDateTime agreeLetterSendDatetime;

    /** 投资款全部到账时间（含时区，所有投资人的资金确认收到时设置） */
    private OffsetDateTime fundsReceivedDatetime;

    /** 生成的协议信链接 */
    private String agreeLetterUrl;

    /** 批准信息 */
    private Approval approval;

    /** 投资记录列表 */
    private List<Investment> investments = new ArrayList<>();

    /** 放款信息 */
    private Disbursement disbursement;

    public Loan(Long borrowerId, BigDecimal principalAmount, BigDecimal interestRate, BigDecimal roi, String currency) {
        this.borrowerId = borrowerId;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.roi = roi;
        this.currency = currency;
        this.currState = LoanStateEnum.PROPOSED;
    }
}
