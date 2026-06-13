package com.hmwssb.works.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_locations", schema = "public")
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "phone_number", length = 20, insertable = false, updatable = false)
    private String phoneNumber;

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

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

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
}
