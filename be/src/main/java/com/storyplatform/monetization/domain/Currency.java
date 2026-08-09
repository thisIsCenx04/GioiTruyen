package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("currencies")
public class Currency {
    @Id
    private UUID id;
    private CurrencyCode code;
    private String name;

    public Currency() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CurrencyCode getCode() { return code; }
    public void setCode(CurrencyCode code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

}
