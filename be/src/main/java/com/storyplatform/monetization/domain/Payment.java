package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("payments")
public class Payment {
    @Id
    private UUID id;
    private UUID userId;
    private UUID paymentMethodId;
    private UUID depositPackageId;
    private Long amountVnd;
    private Long coinReceived;
    private Long gemReceived;
    private String transactionCode;
    private String externalTransactionId;
    private PaymentStatus status;
    private Instant paidAt;
    private Instant createdAt;

    public Payment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(UUID paymentMethodId) { this.paymentMethodId = paymentMethodId; }

    public UUID getDepositPackageId() { return depositPackageId; }
    public void setDepositPackageId(UUID depositPackageId) { this.depositPackageId = depositPackageId; }

    public Long getAmountVnd() { return amountVnd; }
    public void setAmountVnd(Long amountVnd) { this.amountVnd = amountVnd; }

    public Long getCoinReceived() { return coinReceived; }
    public void setCoinReceived(Long coinReceived) { this.coinReceived = coinReceived; }

    public Long getGemReceived() { return gemReceived; }
    public void setGemReceived(Long gemReceived) { this.gemReceived = gemReceived; }

    public String getTransactionCode() { return transactionCode; }
    public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }

    public String getExternalTransactionId() { return externalTransactionId; }
    public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
