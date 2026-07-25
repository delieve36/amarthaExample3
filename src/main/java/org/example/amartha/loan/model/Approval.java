package org.example.amartha.loan.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 批准信息 — 实地验证员确认贷款申请时记录的证明资料。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Approval {

    /** 审批ID **/
    private Long id;

    /** 关联贷款 ID（冗余字段，便于按贷款追溯批准记录） */
    private Long loanId;

    /** 实地验证员的员工 ID */
    private String validatorEmployeeId;

    /** 实地验证员姓名（非必填） */
    private String validatorEmployeeName;

    /** 批准时间（含时区） */
    private OffsetDateTime approvalDatetime;

    /** 实地验证员上传的证明图片 URL 列表（可能有多张照片） */
    private List<String> validatorPhotoUrls = new ArrayList<>();
}
