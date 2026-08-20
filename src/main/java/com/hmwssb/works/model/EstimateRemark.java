package com.hmwssb.works.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "estimate_remarks", schema = "public", indexes = {
    @Index(name = "idx_est_rem_est_id", columnList = "estimate_id")
})
public class EstimateRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "estimate_id", nullable = false)
    private Integer estimateId;

    @Column(name = "revision_number")
    private Integer revisionNumber;

    @Column(name = "officer_phone", length = 20, nullable = false)
    private String officerPhone;

    @Column(name = "officer_name", length = 100)
    private String officerName;

    @Column(name = "officer_role", length = 50)
    private String officerRole;

    @Column(name = "officer_designation", length = 100)
    private String officerDesignation;

    @Column(name = "action", length = 50, nullable = false)
    private String action; // FORWARD, RETURN, REVISION, COMMENT

    @Column(name = "from_status", length = 50)
    private String fromStatus;

    @Column(name = "to_status", length = 50)
    private String toStatus;

    @Column(name = "remarks", length = 2000, nullable = false)
    private String remarks;

    @Column(name = "tags", length = 500)
    private String tags;

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

    public String getOfficerPhone() { return officerPhone; }
    public void setOfficerPhone(String officerPhone) { this.officerPhone = officerPhone; }

    public String getOfficerName() { return officerName; }
    public void setOfficerName(String officerName) { this.officerName = officerName; }

    public String getOfficerRole() { return officerRole; }
    public void setOfficerRole(String officerRole) { this.officerRole = officerRole; }

    public String getOfficerDesignation() { return officerDesignation; }
    public void setOfficerDesignation(String officerDesignation) { this.officerDesignation = officerDesignation; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
