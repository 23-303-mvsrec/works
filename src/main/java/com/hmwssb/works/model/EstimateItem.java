package com.hmwssb.works.model;

import jakarta.persistence.*;

@Entity
@Table(name = "estimate_items", schema = "public")
public class EstimateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "sno")
    private Integer sno;

    @Column(name = "is_material", length = 10)
    private String isMaterial;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "num")
    private Double num;

    @Column(name = "length")
    private Double length;

    @Column(name = "breadth")
    private Double breadth;

    @Column(name = "depth")
    private Double depth;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "rate")
    private Double rate;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "amount")
    private Double amount;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getSno() { return sno; }
    public void setSno(Integer sno) { this.sno = sno; }

    public String getIsMaterial() { return isMaterial; }
    public void setIsMaterial(String isMaterial) { this.isMaterial = isMaterial; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getNum() { return num; }
    public void setNum(Double num) { this.num = num; }

    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }

    public Double getBreadth() { return breadth; }
    public void setBreadth(Double breadth) { this.breadth = breadth; }

    public Double getDepth() { return depth; }
    public void setDepth(Double depth) { this.depth = depth; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
