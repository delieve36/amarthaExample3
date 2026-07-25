package org.example.amartha.loan.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 投资 — 投资者对贷款的投资记录。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Investment {

    /** 投资记录 ID */
    private Long id;

    /** 关联贷款 ID */
    private Long loanId;

    /** 投资者 ID（关联 Investor 实体） */
    private String investorId;

    /** 投资者姓名（非必填，冗余便于展示） */
    private String investorName;

    /** 投资金额（以货币最小单位计，如 RMB 分、IDR 仙） */
    private BigDecimal amount;

    /** 币种（如 CNY、IDR、USD） */
    private String currency;

    /** 投资时间（含时区） */
    private OffsetDateTime datetime;

    /** 资金是否已到账 */
    private boolean fundReceived;
}
