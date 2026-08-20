package com.hmwssb.works.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "estimate_revisions", schema = "public", indexes = {
    @Index(name = "idx_est_rev_est_id", columnList = "estimate_id"),
    @Index(name = "idx_est_rev_number", columnList = "estimate_id, revision_number")
})
public class EstimateRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "estimate_id", nullable = false)
    private Integer estimateId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "revision_type", length = 50, nullable = false)
    private String revisionType;

    @Column(name = "status_at_revision", length = 50)
    private String statusAtRevision;

    @Column(name = "name_of_work", length = 500)
    private String nameOfWork;

    @Column(name = "gst_percent")
    private Double gstPercent;

    @Column(name = "unforeseen_amount")
    private Double unforeseenAmount = 0.0;

    @Column(name = "grand_total")
    private Double grandTotal;

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

    @Column(name = "officer_name", length = 100)
    private String officerName;

    @Column(name = "officer_role", length = 50)
    private String officerRole;

    @Column(name = "officer_designation", length = 100)
    private String officerDesignation;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    @Column(name = "change_summary", length = 2000)
    private String changeSummary;

    @Column(name = "snapshot_json", columnDefinition = "TEXT", nullable = false)
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ── Getters & Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getEstimateId() { return estimateId; }
    public void setEstimateId(Integer estimateId) { this.estimateId = estimateId; }

    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }

    public String getRevisionType() { return revisionType; }
    public void setRevisionType(String revisionType) { this.revisionType = revisionType; }

    public String getStatusAtRevision() { return statusAtRevision; }
    public void setStatusAtRevision(String statusAtRevision) { this.statusAtRevision = statusAtRevision; }

    public String getNameOfWork() { return nameOfWork; }
    public void setNameOfWork(String nameOfWork) { this.nameOfWork = nameOfWork; }

    public Double getGstPercent() { return gstPercent; }
    public void setGstPercent(Double gstPercent) { this.gstPercent = gstPercent; }

    public Double getUnforeseenAmount() { return unforeseenAmount; }
    public void setUnforeseenAmount(Double unforeseenAmount) { this.unforeseenAmount = unforeseenAmount; }

    public Double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }

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

    public String getOfficerName() { return officerName; }
    public void setOfficerName(String officerName) { this.officerName = officerName; }

    public String getOfficerRole() { return officerRole; }
    public void setOfficerRole(String officerRole) { this.officerRole = officerRole; }

    public String getOfficerDesignation() { return officerDesignation; }
    public void setOfficerDesignation(String officerDesignation) { this.officerDesignation = officerDesignation; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
