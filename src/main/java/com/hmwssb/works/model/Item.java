package com.hmwssb.works.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the public.itemlist table in PostgreSQL.
 *
 * Assumed schema:
 *   CREATE TABLE public.itemlist (
 *       slno             INTEGER PRIMARY KEY,
 *       item_description TEXT,
 *       unit             VARCHAR(50),
 *       rate             NUMERIC(12, 100)
 *   );
 *
 * If your column names differ, update @Column(name = "...") accordingly.
 */
@Entity
@Table(name = "itemlist", schema = "public")
public class Item {

    @Id
    @Column(name = "slno")
    private Integer slno;

    @Column(name = "item_description", columnDefinition = "TEXT")
    private String itemDescription;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "rate")
    private Double rate;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getSlno() { return slno; }
    public void setSlno(Integer slno) { this.slno = slno; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
}
