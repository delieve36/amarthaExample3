# Loan Service — 题目3 设计方案

## 概述

实现一个贷款引擎的 RESTful API，管理贷款从创建到发放的完整生命周期。
贷款状态按规则单向流转：`proposed → approved → invested → disbursed`。

---

## 领域模型

### Loan（贷款）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 贷款唯一标识 |
| `borrowerId` | `String` | 借款人 ID |
| `principal` | `BigDecimal` | 本金金额 |
| `rate` | `BigDecimal` | 利率（定义借款人需支付的总利息） |
| `roi` | `BigDecimal` | 投资回报率（定义投资者获得的总利润） |
| `state` | `LoanState` | 当前状态（枚举） |
| `approval` | `Approval` | 批准信息（可为 null） |
| `investments` | `List<Investment>` | 投资列表 |
| `disbursement` | `Disbursement` | 放款信息（可为 null） |
| `agreementLetterUrl` | `String` | 生成的协议信链接 |

### LoanState（枚举）

状态定义：`PROPOSED → APPROVED → INVESTED → DISBURSED`

### Approval（批准信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| `fieldValidatorPhotoUrl` | `String` | 实地验证员访问借款人的图片证明 URL |
| `fieldValidatorEmployeeId` | `String` | 实地验证员的员工 ID |
| `approvalDate` | `LocalDate` | 批准日期 |

### Investment（投资）

| 字段 | 类型 | 说明 |
|------|------|------|
| `investorId` | `String` | 投资者 ID |
| `amount` | `BigDecimal` | 投资金额 |

### Disbursement（放款信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| `signedAgreementUrl` | `String` | 借款人签署的协议信（pdf/jpeg）URL |
| `fieldOfficerEmployeeId` | `String` | 现场员工 ID |
| `disbursementDate` | `LocalDate` | 放款日期 |

---

## 状态机规则

```
┌──────────┐  approve()  ┌──────────┐  invest()  ┌──────────┐  disburse()  ┌──────────┐
│ PROPOSED │ ──────────▶ │ APPROVED │ ──────────▶ │ INVESTED │ ────────────▶ │ DISBURSED │
└──────────┘             └──────────┘             └──────────┘              └───────────┘
```

- **PROPOSED → APPROVED**：必须提供批准信息（照片证明、员工ID、日期）；不可回退。
- **APPROVED → INVESTED**：总投资额必须等于贷款本金；可有多位投资者；总投资额不可超过本金；投资完成后发送邮件通知（含协议信链接）。
- **INVESTED → DISBURSED**：必须提供借款人签署的协议信、现场员工ID、放款日期。

---

## RESTful API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/loans` | 创建贷款（初始状态 PROPOSED） |
| `GET` | `/api/loans/{id}` | 查询贷款详情 |
| `PATCH` | `/api/loans/{id}/approve` | 批准贷款（PROPOSED → APPROVED） |
| `POST` | `/api/loans/{id}/investments` | 投资者投资（累计到 APPROVED 的贷款上） |
| `PATCH` | `/api/loans/{id}/disburse` | 放款（INVESTED → DISBURSED） |

### 请求/响应示例

**创建贷款：**
```json
POST /api/loans
{
  "borrowerId": "BORROWER001",
  "principal": 5000000,
  "rate": 10,
  "roi": 8
}
```

**批准贷款：**
```json
PATCH /api/loans/{id}/approve
{
  "fieldValidatorPhotoUrl": "https://storage.example.com/photo.jpg",
  "fieldValidatorEmployeeId": "EMP001",
  "approvalDate": "2025-07-25"
}
```

**投资：**
```json
POST /api/loans/{id}/investments
{
  "investorId": "INV001",
  "amount": 3000000
}
```

**放款：**
```json
PATCH /api/loans/{id}/disburse
{
  "signedAgreementUrl": "https://storage.example.com/agreement.pdf",
  "fieldOfficerEmployeeId": "EMP002",
  "disbursementDate": "2025-07-25"
}
```

---

## 包结构

```
org.example.amartha.loan
├── LoanApplication.java          # Spring Boot 入口
├── controller/
│   └── LoanController.java       # REST 控制器
├── model/
│   ├── Loan.java                 # 贷款实体
│   ├── LoanState.java            # 状态枚举
│   ├── Approval.java             # 批准信息（值对象）
│   ├── Investment.java           # 投资（值对象）
│   └── Disbursement.java         # 放款信息（值对象）
├── dto/
│   ├── CreateLoanRequest.java
│   ├── ApproveLoanRequest.java
│   ├── InvestRequest.java
│   ├── DisburseRequest.java
│   └── LoanResponse.java
├── service/
│   ├── LoanService.java          # 业务逻辑 + 状态机
│   └── NotificationService.java  # 通知服务（发送邮件）
└── repository/
    └── LoanRepository.java       # 数据访问层
```

---

## 技术选型

| 组件 | 选择 | 说明 |
|------|------|------|
| 语言 | Java 21 | 项目要求 |
| 框架 | Spring Boot 3.x | 成熟的 REST API 框架 |
| 构建工具 | Maven | 项目已有 |
| 数据库 | H2（内存）/ PostgreSQL | 开发用 H2，生产用 PG |
| ORM | Spring Data JPA | 简化数据访问 |
| 测试 | JUnit 5 + Mockito | 单元测试 + 集成测试 |

---

## 待讨论事项

1. 数据库选择：H2 内存数据库还是需要配置 PostgreSQL？
2. 通知服务：邮件发送是否需要真实实现还是接口桩？
3. 文件存储：图片/PDF 的存储是本地文件系统还是模拟 URL？
4. 是否引入 Spring Boot Starter Web 之外的其他依赖？
5. 测试覆盖范围：是否要求集成测试？
