package com.hmwssb.works.repository;

import com.hmwssb.works.model.EstimateRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstimateRevisionRepository extends JpaRepository<EstimateRevision, Long> {
    List<EstimateRevision> findByEstimateIdOrderByRevisionNumberAsc(Integer estimateId);
    List<EstimateRevision> findByEstimateIdOrderByRevisionNumberDesc(Integer estimateId);
    Optional<EstimateRevision> findByEstimateIdAndRevisionNumber(Integer estimateId, Integer revisionNumber);
    long countByEstimateId(Integer estimateId);
    void deleteByEstimateId(Integer estimateId);
}
