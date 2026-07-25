# Loan Service — 题目3 设计方案

## 概述

实现一个贷款引擎的 RESTful API，管理贷款从创建到发放的完整生命周期。
贷款状态按规则单向流转：`proposed → approved → invested → disbursed`。
贷款的核心属性为：`borrowerId`（借款人 ID，字符串）、`principalAmount`（本金金额）、
`rate`（利率，决定借款人需支付的总利息）、
`roi`（投资回报率，决定投资者获得的总利润）、
`agreementLetterUrl`（放款后生成的协议信链接）
---

## 领域模型

### Loan（贷款）

| 字段                 | 类型               | 必填 | 说明                                 |
|----------------------|--------------------|------|--------------------------------------|
| `id`                 | `Long`             | —    | 贷款唯一标识（自增主键）             |
| `borrowerId`         | `String`           | Y    | 借款人 ID                            |
| `principalAmount`    | `BigDecimal`       | Y    | 本金金额（must > 0 取货币最小单位）  |
| `rate`               | `BigDecimal`       | Y    | 利率（定义借款人需支付的总利息）     |
| `roi`                | `BigDecimal`       | Y    | 投资回报率（定义投资者获得的总利润） |
| `notes`              | `String`           | N    | 贷款备注                             |
| `state`              | `LoanStateEnum`    | —    | 当前状态枚举                         |
| `approval`           | `Approval`         | —    | 批准信息（可为 null）                |
| `investments`        | `List<Investment>` | —    | 投资列表                             |
| `disbursement`       | `Disbursement`     | —    | 放款信息（可为 null）                |
| `agreementLetterUrl` | `String`           | —    | 生成的协议信链接                     |

### LoanStateEnum（枚举）

状态定义：`PROPOSED → APPROVED → INVESTED → DISBURSED`

### Approval（批准信息）

| 字段                       | 类型        | 必填 | 说明                               |
|----------------------------|-------------|------|------------------------------------|
| `fieldValidatorPhotoUrl`   | `String`    | Y    | 实地验证员访问借款人的图片证明 URL |
| `fieldValidatorEmployeeId` | `String`    | Y    | 实地验证员的员工 ID                |
| `approvalDate`             | `LocalDate` | Y    | 批准日期                           |

### Investment（投资）

| 字段         | 类型         | 必填 | 说明                 |
|--------------|--------------|------|----------------------|
| `investorId` | `String`     | Y    | 投资者 ID            |
| `amount`     | `BigDecimal` | Y    | 投资金额（must > 0） |

### Disbursement（放款信息）

| 字段                     | 类型        | 必填 | 说明                  |
|--------------------------|-------------|------|-----------------------|
| `signedAgreementUrl`     | `String`    | Y    | 借款人签署的协议信URL |
| `fieldOfficerEmployeeId` | `String`    | Y    | 现场员工 ID           |
| `disbursementDate`       | `LocalDate` | Y    | 放款日期              |

---

## 状态机设计（State Pattern）

采用状态模式实现状态流转，每个状态对应一个独立的处理器类。

```
┌─────────────┐  approve()  ┌─────────────┐  invest()  ┌─────────────┐  disburse()  ┌──────────────┐
│ProposedState│ ──────────▶ │ApprovedState│ ──────────▶│InvestedState│ ────────────▶│DisbursedState│
│             │             │             │            │             │              │              │
│ 仅允许审批  │             │ 仅允许投资  │            │ 仅允许放款  │              │ 所有操作拒绝 │
└─────────────┘             └─────────────┘            └─────────────┘              └──────────────┘
```

### 状态转换规则

- **PROPOSED → APPROVED**：必须提供批准信息（照片证明、员工ID、日期）；不可回退。
- **APPROVED → INVESTED**：总投资额必须等于贷款本金；可有多位投资者；总投资额不可超过本金；投资完成后发送邮件通知（含协议信链接）。
- **INVESTED → DISBURSED**：必须提供借款人签署的协议信、现场员工ID、放款日期。

### 类结构

```
state/
├── LoanStateHandler.java      # 状态接口 — 定义 approve/invest/disburse 方法
├── ProposedState.java         # 初始状态 — 仅 approve() 允许
├── ApprovedState.java         # 已批准  — 仅 invest() 允许（可部分投资）
├── InvestedState.java         # 已满投  — 仅 disburse() 允许
└── DisbursedState.java        # 已放款  — 所有操作拒绝（终止态）
```

Loan 实体通过 `LoanStateHandler.forState(state)` 获取当前状态处理器并委托调用。

---

## 参数校验设计

采用 **两层校验**：

| 层级   | 位置             | 机制                                              | 作用                                               |
|--------|------------------|---------------------------------------------------|----------------------------------------------------|
| API 层 | Controller + DTO | `@Valid` + `@NotBlank` / `@NotNull` / `@Positive` | 请求到达 Service 前快速失败                        |
| 领域层 | State Handler    | 显式 if/throw                                     | 确保绕过 Controller 的调用（如内部调用）也经过校验 |

### 必填 vs 非必填字段

| API                                | 必填字段                                                             | 非必填字段 |
|------------------------------------|----------------------------------------------------------------------|------------|
| `POST /api/loans`                  | `borrowerId`, `principal`, `rate`, `roi`                             | `notes`    |
| `PATCH /api/loans/{id}/approve`    | `fieldValidatorPhotoUrl`, `fieldValidatorEmployeeId`, `approvalDate` | —          |
| `POST /api/loans/{id}/investments` | `investorId`, `amount`                                               | —          |
| `PATCH /api/loans/{id}/disburse`   | `signedAgreementUrl`, `fieldOfficerEmployeeId`, `disbursementDate`   | —          |

---

## RESTful API 设计

| 方法    | 路径                          | 说明                                                              |
|:--------|:------------------------------|:------------------------------------------------------------------|
| `POST`  | `/api/loans`                  | 创建贷款（初始状态 PROPOSED）                                     |
| `GET`   | `/api/loans/{id}`             | 查询贷款详情                                                      |
| `PATCH` | `/api/loans/{id}/approve`     | 批准贷款（PROPOSED → APPROVED）                                   |
| `POST`  | `/api/loans/{id}/investments` | 投资者投资（累计至 APPROVED 的贷款，达到本金后自动变为 INVESTED） |
| `PATCH` | `/api/loans/{id}/disburse`    | 放款（INVESTED → DISBURSED）                                      |

### 请求/响应示例

**创建贷款（含非必填 notes）：**

`POST /api/loans`

```json
{
  "borrowerId": "BORROWER001",
  "principal": 5000000,
  "rate": 10,
  "roi": 8,
  "notes": "用于创业的小额贷款"
}
```

**批准贷款：**

`PATCH /api/loans/{id}/approve`

```json
{
  "fieldValidatorPhotoUrl": "https://storage.example.com/photo.jpg",
  "fieldValidatorEmployeeId": "EMP001",
  "approvalDate": "2026-07-25"
}
```

**投资：**

`POST /api/loans/{id}/investments`

```json
{
  "investorId": "INV001",
  "amount": 3000000
}
```

**放款：**

`PATCH /api/loans/{id}/disburse`

```json
{
  "signedAgreementUrl": "https://storage.example.com/agreement.pdf",
  "fieldOfficerEmployeeId": "EMP002",
  "disbursementDate": "2026-07-25"
}
```

---

## 包结构

```
org.example.amartha.loan
├── LoanApplication.java          # Spring Boot 入口
├── controller/
│   ├── LoanController.java       # REST 控制器
│   └── GlobalExceptionHandler.java
├── model/
│   ├── Loan.java                 # 贷款实体（含状态委托）
│   ├── LoanStateEnum.java        # 状态枚举
│   ├── Approval.java             # 批准信息（值对象）
│   ├── Investment.java           # 投资（值对象）
│   └── Disbursement.java         # 放款信息（值对象）
├── state/
│   ├── LoanStateHandler.java     # 状态接口
│   ├── ProposedState.java        # PROPOSED 状态处理器
│   ├── ApprovedState.java        # APPROVED 状态处理器
│   ├── InvestedState.java        # INVESTED 状态处理器
│   └── DisbursedState.java       # DISBURSED 状态处理器
├── dto/
│   ├── CreateLoanRequest.java
│   ├── ApproveLoanRequest.java
│   ├── InvestRequest.java
│   ├── DisburseRequest.java
│   └── LoanResponse.java
├── service/
│   ├── LoanService.java          # 业务逻辑 + 状态机编排
│   └── NotificationService.java  # 通知服务（日志桩）
└── repository/
    └── LoanRepository.java       # JPA Repository
```

---

## 技术选型

| 组件     | 选择                                                | 说明                                              |
|----------|-----------------------------------------------------|---------------------------------------------------|
| 语言     | Java 21                                             | 项目要求                                          |
| 框架     | Spring Boot 3.5.x                                   | REST API + DI + Validation                        |
| 构建工具 | Maven                                               | 项目已有                                          |
| 数据库   | H2（内存）                                          | 开发/演示用                                       |
| ORM      | Spring Data JPA                                     | 简化数据访问                                      |
| 代码简化 | Lombok                                              | 替代手写 getter/setter/构造器                     |
| 参数校验 | Jakarta Validation + Spring Boot Starter Validation | `@Valid` + `@NotBlank` / `@NotNull` / `@Positive` |
| 测试     | JUnit 5 + Mockito + MockMvc                         | 单元测试 + Controller 集成测试                    |
