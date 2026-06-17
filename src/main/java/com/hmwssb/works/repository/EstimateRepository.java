package com.hmwssb.works.repository;

import com.hmwssb.works.model.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EstimateRepository extends JpaRepository<Estimate, Integer> {
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Estimate e SET e.officerPhone = :newPhone WHERE e.officerPhone = :oldPhone")
    void updateOfficerPhone(@Param("oldPhone") String oldPhone, @Param("newPhone") String newPhone);
}

