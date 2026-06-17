package com.hmwssb.works.repository;

import com.hmwssb.works.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOfficerPhoneOrderByTimestampDesc(String officerPhone);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    List<AuditLog> findAllByOrderByTimestampDesc();

    List<AuditLog> findByActionOrderByTimestampDesc(String action);
}
