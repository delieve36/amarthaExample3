package org.example.amartha.loan.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 投资者 — 投资人的基本档案信息。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Investor {

    /** 数据库自增主键 */
    private Long id;

    /** 投资者唯一标识 */
    private Long investorId;

    /** 投资者姓名 */
    private String name;

    /** 投资者邮箱（用于发送协议信等通知） */
    private String emailUrl;

    /** 注册日期 */
    private LocalDate registerDate;
}
