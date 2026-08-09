package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("purchase_order_items")
public class PurchaseOrderItem {
    private UUID orderId;
    private UUID chapterId;
    private Long coinPrice;

    public PurchaseOrderItem() {}

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public Long getCoinPrice() { return coinPrice; }
    public void setCoinPrice(Long coinPrice) { this.coinPrice = coinPrice; }

}
