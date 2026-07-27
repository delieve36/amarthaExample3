package org.example.amartha.loan.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 放款信息 — 贷款发放给借款人时记录的凭证和经办人信息。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Disbursement {

    /** 放款记录 ID */
    private Long id;

    /** 记录创建时间（服务器时区） */
    private LocalDateTime gmtCreate;

    /** 记录修改时间（服务器时区） */
    private LocalDateTime gmtModify;

    /** 关联贷款 ID */
    private Long loanId;

    /** 借款人签署的协议信（pdf/jpeg）URL */
    private String signedAgreementUrl;

    /** 负责交付资金的现场员工 ID */
    private Long fieldOfficerEmployeeId;

    /** 现场员工姓名（非必填） */
    private String fieldOfficerEmployeeName;

    /** 放款时间（含时区） */
    private OffsetDateTime disbursementDatetime;

    /** 放款是否已执行 */
    private boolean disbursed;
}
