package com.hmwssb.works.service;

import com.hmwssb.works.model.AuditLog;
import com.hmwssb.works.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("com.hmwssb.works.audit");

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persist an audit event to the database and write to audit.log file.
     */
    public void log(String action, String entityType, String entityId,
                    String officerPhone, String officerName, String officerRole,
                    String details, String previousStatus, String newStatus,
                    String ipAddress) {

        // 1. Save to database
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOfficerPhone(officerPhone);
        entry.setOfficerName(officerName);
        entry.setOfficerRole(officerRole);
        entry.setDetails(details);
        entry.setPreviousStatus(previousStatus);
        entry.setNewStatus(newStatus);
        entry.setIpAddress(ipAddress);
        auditLogRepository.save(entry);

        // 2. Write to audit.log file
        auditLog.info("ACTION={} ENTITY={}/{} OFFICER={}/{}({}) STATUS={}→{} DETAILS=\"{}\" IP={}",
                action,
                entityType, entityId,
                officerName, officerPhone, officerRole,
                previousStatus, newStatus,
                details,
                ipAddress);
    }

    /**
     * Convenience overload — no status transition.
     */
    public void log(String action, String entityType, String entityId,
                    String officerPhone, String officerName, String officerRole,
                    String details, String ipAddress) {
        log(action, entityType, entityId, officerPhone, officerName, officerRole,
                details, null, null, ipAddress);
    }
}
