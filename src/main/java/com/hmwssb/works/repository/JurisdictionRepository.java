package com.hmwssb.works.repository;

import com.hmwssb.works.model.Jurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JurisdictionRepository extends JpaRepository<Jurisdiction, Integer> {
}
