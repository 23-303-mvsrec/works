package com.hmwssb.works.repository;

import com.hmwssb.works.model.EstimateRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstimateRemarkRepository extends JpaRepository<EstimateRemark, Long> {
    List<EstimateRemark> findByEstimateIdOrderByCreatedAtAsc(Integer estimateId);
    List<EstimateRemark> findByEstimateIdOrderByCreatedAtDesc(Integer estimateId);
    void deleteByEstimateId(Integer estimateId);
}
