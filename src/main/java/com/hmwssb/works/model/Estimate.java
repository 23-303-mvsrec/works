package com.hmwssb.works.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "estimates", schema = "public")
public class Estimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name_of_work", length = 500)
    private String nameOfWork;

    @Column(name = "gst_percent")
    private Double gstPercent;

    @Column(name = "grand_total")
    private Double grandTotal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "estimate_id")
    @OrderBy("sno ASC")
    private List<EstimateItem> items = new ArrayList<>();

    @Column(name = "unforeseen_amount")
    private Double unforeseenAmount = 0.0;

    @Column(name = "corp", length = 100)
    private String corp;

    @Column(name = "zone_name", length = 200)
    private String zoneName;

    @Column(name = "division", length = 100)
    private String division;

    @Column(name = "circle_name", length = 200)
    private String circleName;

    @Column(name = "ward_name", length = 200)
    private String wardName;

    @Column(name = "officer_phone", length = 20)
    private String officerPhone;

    @Column(name = "status", length = 50)
    private String status = "DRAFT";

    @Column(name = "prepared_by_name", length = 100)
    private String preparedByName;

    @Column(name = "prepared_by_designation", length = 100)
    private String preparedByDesignation;

    @Column(name = "verified_by_name", length = 100)
    private String verifiedByName;

    @Column(name = "verified_by_designation", length = 100)
    private String verifiedByDesignation;

    @Column(name = "recommended_by_name", length = 100)
    private String recommendedByName;

    @Column(name = "recommended_by_designation", length = 100)
    private String recommendedByDesignation;

    @Column(name = "forwarded_by_name", length = 100)
    private String forwardedByName;

    @Column(name = "forwarded_by_designation", length = 100)
    private String forwardedByDesignation;

    @Column(name = "sanctioned_by_name", length = 100)
    private String sanctionedByName;

    @Column(name = "sanctioned_by_designation", length = 100)
    private String sanctionedByDesignation;

    // ── Lifecycle Hook ──────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "DRAFT";
        }
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNameOfWork() { return nameOfWork; }
    public void setNameOfWork(String nameOfWork) { this.nameOfWork = nameOfWork; }

    public Double getGstPercent() { return gstPercent; }
    public void setGstPercent(Double gstPercent) { this.gstPercent = gstPercent; }

    public Double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }

    public Double getUnforeseenAmount() { return unforeseenAmount; }
    public void setUnforeseenAmount(Double unforeseenAmount) { this.unforeseenAmount = unforeseenAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<EstimateItem> getItems() { return items; }
    public void setItems(List<EstimateItem> items) { this.items = items; }

    public String getCorp() { return corp; }
    public void setCorp(String corp) { this.corp = corp; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getCircleName() { return circleName; }
    public void setCircleName(String circleName) { this.circleName = circleName; }

    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }

    public String getOfficerPhone() { return officerPhone; }
    public void setOfficerPhone(String officerPhone) { this.officerPhone = officerPhone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPreparedByName() { return preparedByName; }
    public void setPreparedByName(String preparedByName) { this.preparedByName = preparedByName; }

    public String getPreparedByDesignation() { return preparedByDesignation; }
    public void setPreparedByDesignation(String preparedByDesignation) { this.preparedByDesignation = preparedByDesignation; }

    public String getVerifiedByName() { return verifiedByName; }
    public void setVerifiedByName(String verifiedByName) { this.verifiedByName = verifiedByName; }

    public String getVerifiedByDesignation() { return verifiedByDesignation; }
    public void setVerifiedByDesignation(String verifiedByDesignation) { this.verifiedByDesignation = verifiedByDesignation; }

    public String getRecommendedByName() { return recommendedByName; }
    public void setRecommendedByName(String recommendedByName) { this.recommendedByName = recommendedByName; }

    public String getRecommendedByDesignation() { return recommendedByDesignation; }
    public void setRecommendedByDesignation(String recommendedByDesignation) { this.recommendedByDesignation = recommendedByDesignation; }

    public String getForwardedByName() { return forwardedByName; }
    public void setForwardedByName(String forwardedByName) { this.forwardedByName = forwardedByName; }

    public String getForwardedByDesignation() { return forwardedByDesignation; }
    public void setForwardedByDesignation(String forwardedByDesignation) { this.forwardedByDesignation = forwardedByDesignation; }

    public String getSanctionedByName() { return sanctionedByName; }
    public void setSanctionedByName(String sanctionedByName) { this.sanctionedByName = sanctionedByName; }

    public String getSanctionedByDesignation() { return sanctionedByDesignation; }
    public void setSanctionedByDesignation(String sanctionedByDesignation) { this.sanctionedByDesignation = sanctionedByDesignation; }
}
