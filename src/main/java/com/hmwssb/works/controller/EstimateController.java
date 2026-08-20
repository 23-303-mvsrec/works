package com.hmwssb.works.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmwssb.works.model.*;
import com.hmwssb.works.repository.EstimateRemarkRepository;
import com.hmwssb.works.repository.EstimateRepository;
import com.hmwssb.works.repository.EstimateRevisionRepository;
import com.hmwssb.works.repository.UserRepository;
import com.hmwssb.works.service.AuditService;
import com.hmwssb.works.service.EstimateDiffService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/estimates")
@CrossOrigin(origins = "*")
@Transactional
public class EstimateController {

    private static final Logger log = LoggerFactory.getLogger(EstimateController.class);

    private final EstimateRepository estimateRepository;
    private final EstimateRevisionRepository estimateRevisionRepository;
    private final EstimateRemarkRepository estimateRemarkRepository;
    private final EstimateDiffService estimateDiffService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    public EstimateController(EstimateRepository estimateRepository,
                              EstimateRevisionRepository estimateRevisionRepository,
                              EstimateRemarkRepository estimateRemarkRepository,
                              EstimateDiffService estimateDiffService,
                              UserRepository userRepository,
                              AuditService auditService,
                              HttpServletRequest request,
                              ObjectMapper objectMapper) {
        this.estimateRepository = estimateRepository;
        this.estimateRevisionRepository = estimateRevisionRepository;
        this.estimateRemarkRepository = estimateRemarkRepository;
        this.estimateDiffService = estimateDiffService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.request = request;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Estimate estimate) {
        String phone = estimate.getOfficerPhone();
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Officer phone is required.");
        }

        return userRepository.findById(phone.strip())
            .map(user -> {
                String role = user.getRole();
                estimate.setCorp(normalize(estimate.getCorp()));
                estimate.setZoneName(normalize(estimate.getZoneName()));
                estimate.setDivision(normalize(estimate.getDivision()));
                estimate.setCircleName(normalize(estimate.getCircleName()));
                estimate.setWardName(normalize(estimate.getWardName()));

                if (estimate.getId() == null) {
                    if (!hasEffectiveRoleForEstimate(user, estimate, "MANAGER")) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body("Only officers with MANAGER role for this location can create a new estimate.");
                    }
                    estimate.setStatus("DRAFT");
                    estimate.setPreparedByName(user.getName());
                    estimate.setPreparedByDesignation(user.getDesignation());
                    recalculateGrandTotal(estimate);
                    Estimate saved = estimateRepository.save(estimate);
                    log.info("Created estimate: id={}, officer={}, nameOfWork={}", saved.getId(), phone, saved.getNameOfWork());

                    createRevisionSnapshot(saved, user, "INITIAL_DRAFT", "Initial draft estimate created", "Initial draft created");
                    recordRemark(saved.getId(), 1, user, "CREATE", null, "DRAFT", "Initial draft created", "Draft");

                    auditService.log("CREATE_ESTIMATE", "Estimate", String.valueOf(saved.getId()),
                            phone, user.getName(), role,
                            "Created estimate: " + saved.getNameOfWork(),
                            null, "DRAFT", getClientIp());
                    return ResponseEntity.ok(saved);
                } else {
                    return estimateRepository.findById(estimate.getId())
                        .map(existing -> {
                            String currentStatus = existing.getStatus();
                            if (currentStatus == null) currentStatus = "DRAFT";

                            String requiredRole = null;
                            if ("DRAFT".equalsIgnoreCase(currentStatus)) requiredRole = "MANAGER";
                            else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) requiredRole = "DGM";
                            else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) requiredRole = "GM";
                            else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) requiredRole = "CGM";

                            boolean allowed = requiredRole != null && hasEffectiveRoleForEstimate(user, existing, requiredRole);

                            if (!allowed) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body("You do not have permission to edit this estimate at its current status: " + currentStatus);
                            }

                            // Optimistic locking check (for authorized officer)
                            if (estimate.getVersion() != null && existing.getVersion() != null && estimate.getVersion() > 0) {
                                if (estimate.getVersion() < existing.getVersion()) {
                                    log.warn("Optimistic locking conflict on estimate {}: incoming version {} vs current version {}",
                                            estimate.getId(), estimate.getVersion(), existing.getVersion());
                                    return ResponseEntity.status(HttpStatus.CONFLICT)
                                            .body("This estimate was just modified by another officer. Please refresh and try again.");
                                }
                            }

                            String changeSummary = estimateDiffService.generateChangeSummary(existing, estimate);

                            existing.setNameOfWork(estimate.getNameOfWork());
                            existing.setGstPercent(estimate.getGstPercent());
                            existing.setUnforeseenAmount(estimate.getUnforeseenAmount());
                            existing.setCorp(normalize(estimate.getCorp()));
                            existing.setZoneName(normalize(estimate.getZoneName()));
                            existing.setDivision(normalize(estimate.getDivision()));
                            existing.setCircleName(normalize(estimate.getCircleName()));
                            existing.setWardName(normalize(estimate.getWardName()));
                            if (estimate.getLastRemarks() != null) {
                                existing.setLastRemarks(estimate.getLastRemarks());
                            }

                            existing.getItems().clear();
                            if (estimate.getItems() != null) {
                                int idx = 1;
                                for (EstimateItem item : estimate.getItems()) {
                                    EstimateItem newItem = new EstimateItem();
                                    newItem.setSno(item.getSno() != null ? item.getSno() : idx);
                                    newItem.setIsMaterial(item.getIsMaterial());
                                    newItem.setDescription(item.getDescription());
                                    newItem.setNum(item.getNum());
                                    newItem.setLength(item.getLength());
                                    newItem.setBreadth(item.getBreadth());
                                    newItem.setDepth(item.getDepth());
                                    newItem.setQuantity(item.getQuantity());
                                    newItem.setRate(item.getRate());
                                    newItem.setUnit(item.getUnit());
                                    newItem.setAmount(item.getAmount());
                                    existing.getItems().add(newItem);
                                    idx++;
                                }
                            }

                            recalculateGrandTotal(existing);
                            Estimate saved = estimateRepository.save(existing);
                            log.info("Updated estimate: id={}, officer={}, status={}", saved.getId(), phone, currentStatus);

                            EstimateRevision rev = createRevisionSnapshot(saved, user, "MEASUREMENT_UPDATE", estimate.getLastRemarks(), changeSummary);
                            Integer revNum = rev != null ? rev.getRevisionNumber() : null;
                            recordRemark(saved.getId(), revNum, user, "REVISION", currentStatus, currentStatus, "Measurements updated: " + changeSummary, "Measurement");

                            auditService.log("UPDATE_ESTIMATE", "Estimate", String.valueOf(saved.getId()),
                                    phone, user.getName(), role,
                                    "Updated estimate: " + saved.getNameOfWork() + " (" + changeSummary + ")",
                                    currentStatus, currentStatus, getClientIp());
                            return ResponseEntity.ok(saved);
                        })
                        .map(res -> (ResponseEntity<?>) res)
                        .orElseGet(() -> ResponseEntity.notFound().build());
                }
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found."));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "officerPhone", required = false) String officerPhone,
            @RequestParam(name = "role", required = false) String roleParam,
            @RequestParam(name = "corp", required = false) String corpParam,
            @RequestParam(name = "zoneName", required = false) String zoneNameParam,
            @RequestParam(name = "division", required = false) String divisionParam,
            @RequestParam(name = "circleName", required = false) String circleNameParam,
            @RequestParam(name = "wardName", required = false) String wardNameParam,
            @RequestParam(name = "status", required = false) String statusParam) {

        if (officerPhone == null || officerPhone.trim().isEmpty()) {
            return ResponseEntity.ok(estimateRepository.findAll());
        }

        return userRepository.findById(officerPhone.strip())
            .map(user -> {
                String effectiveRole = (roleParam != null && !roleParam.trim().isEmpty())
                        ? roleParam.trim().toUpperCase()
                        : user.getRole().toUpperCase();

                boolean hasExplicitLocationFilter = (corpParam != null && !corpParam.trim().isEmpty())
                        || (zoneNameParam != null && !zoneNameParam.trim().isEmpty())
                        || (divisionParam != null && !divisionParam.trim().isEmpty())
                        || (circleNameParam != null && !circleNameParam.trim().isEmpty())
                        || (wardNameParam != null && !wardNameParam.trim().isEmpty());

                if (hasExplicitLocationFilter && !"ADMIN".equals(effectiveRole) && !"DOP".equals(effectiveRole)) {
                    boolean hasPermissionForScope = isOfficerAuthorizedForScope(user, effectiveRole, corpParam, zoneNameParam, divisionParam, circleNameParam, wardNameParam);
                    if (!hasPermissionForScope) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<List<Estimate>>body(Collections.emptyList());
                    }
                }

                List<Estimate> all = estimateRepository.findAll();
                List<Estimate> filtered = all.stream().filter(est -> {
                    if (statusParam != null && !statusParam.trim().isEmpty()) {
                        if (!statusParam.equalsIgnoreCase(est.getStatus())) return false;
                    }
                    if (corpParam != null && !corpParam.trim().isEmpty()) {
                        if (!safeEquals(est.getCorp(), corpParam)) return false;
                    }
                    if (zoneNameParam != null && !zoneNameParam.trim().isEmpty()) {
                        if (!safeEquals(est.getZoneName(), zoneNameParam)) return false;
                    }
                    if (divisionParam != null && !divisionParam.trim().isEmpty()) {
                        if (!safeEquals(est.getDivision(), divisionParam)) return false;
                    }
                    if (circleNameParam != null && !circleNameParam.trim().isEmpty()) {
                        if (!safeEquals(est.getCircleName(), circleNameParam)) return false;
                    }
                    if (wardNameParam != null && !wardNameParam.trim().isEmpty()) {
                        if (!safeEquals(est.getWardName(), wardNameParam)) return false;
                    }

                    if ("ADMIN".equals(effectiveRole) || "DOP".equals(effectiveRole)) {
                        return true;
                    }

                    List<UserLocation> matchingLocs = user.getLocations().stream().filter(loc -> {
                        String locRole = loc.getRole() != null ? loc.getRole().toUpperCase() : user.getRole().toUpperCase();
                        return locRole.equals(effectiveRole);
                    }).toList();

                    if (matchingLocs.isEmpty()) {
                        if (effectiveRole.equals(user.getRole().toUpperCase())) {
                            matchingLocs = user.getLocations();
                        }
                    }

                    if (matchingLocs.isEmpty()) {
                        return user.getPhoneNumber().equals(est.getOfficerPhone());
                    }

                    for (UserLocation loc : matchingLocs) {
                        if (isLocationMatchingEstimate(loc, effectiveRole, est.getCorp(), est.getZoneName(), est.getDivision(), est.getCircleName(), est.getWardName())) {
                            return true;
                        }
                    }

                    return user.getPhoneNumber().equals(est.getOfficerPhone());
                }).toList();

                return ResponseEntity.ok(filtered);
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.emptyList()));
    }

    private boolean isOfficerAuthorizedForScope(User user, String role, String corp, String zoneName, String division, String circleName, String wardName) {
        if (user == null) return false;
        String r = role.toUpperCase();
        if ("ADMIN".equals(r) || "DOP".equals(r)) return true;

        List<UserLocation> locs = user.getLocations().stream().filter(loc -> {
            String lRole = loc.getRole() != null ? loc.getRole().toUpperCase() : user.getRole().toUpperCase();
            return lRole.equals(r);
        }).toList();

        if (locs.isEmpty()) {
            if (r.equals(user.getRole().toUpperCase())) {
                locs = user.getLocations();
            }
        }
        if (locs.isEmpty()) return false;

        for (UserLocation loc : locs) {
            if ("CGM".equals(r)) {
                if (corp != null && !corp.trim().isEmpty() && loc.getCorp() != null && !safeEquals(loc.getCorp(), corp)) continue;
                if (zoneName != null && !zoneName.trim().isEmpty() && loc.getZoneName() != null && !safeEquals(loc.getZoneName(), zoneName)) continue;
                return true;
            }
            if ("GM".equals(r)) {
                if (zoneName != null && !zoneName.trim().isEmpty() && loc.getZoneName() != null && !safeEquals(loc.getZoneName(), zoneName)) continue;
                if (division != null && !division.trim().isEmpty() && loc.getDivision() != null && !safeEquals(loc.getDivision(), division)) continue;
                return true;
            }
            if ("DGM".equals(r)) {
                if (division != null && !division.trim().isEmpty() && loc.getDivision() != null && !safeEquals(loc.getDivision(), division)) continue;
                if (circleName != null && !circleName.trim().isEmpty() && loc.getCircleName() != null && !safeEquals(loc.getCircleName(), circleName)) continue;
                return true;
            }
            if ("MANAGER".equals(r)) {
                if (circleName != null && !circleName.trim().isEmpty() && loc.getCircleName() != null && !safeEquals(loc.getCircleName(), circleName)) continue;
                if (wardName != null && !wardName.trim().isEmpty() && loc.getWardName() != null && !safeEquals(loc.getWardName(), wardName)) continue;
                return true;
            }
        }
        return false;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable(name = "id") Integer id) {
        return estimateRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable(name = "id") Integer id,
            @RequestParam(name = "officerPhone") String officerPhone) {

        return userRepository.findById(officerPhone.strip())
            .map(user -> {
                String role = user.getRole();
                boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "DOP".equalsIgnoreCase(role);

                if (isAdmin) {
                    return estimateRepository.findById(id)
                        .map(est -> {
                            estimateRevisionRepository.deleteByEstimateId(id);
                            estimateRemarkRepository.deleteByEstimateId(id);
                            estimateRepository.delete(est);
                            log.info("Deleted estimate by Admin/DOP: id={}", id);
                            auditService.log("DELETE_ESTIMATE", "Estimate", String.valueOf(id),
                                    officerPhone, user.getName(), role,
                                    "Admin/DOP deleted estimate: " + est.getNameOfWork(),
                                    est.getStatus(), null, getClientIp());
                            return ResponseEntity.noContent().build();
                        })
                        .orElse(ResponseEntity.notFound().build());
                }

                boolean isManager = "MANAGER".equalsIgnoreCase(role);
                if (isManager) {
                    return estimateRepository.findById(id)
                        .map(est -> {
                            if (!user.getPhoneNumber().equals(est.getOfficerPhone()) && !isEstimateInOfficerJurisdiction(user, est)) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body("You can only delete estimates you created or within your assigned ward jurisdiction.");
                            }
                            if (!"DRAFT".equalsIgnoreCase(est.getStatus())) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body("Estimates can only be deleted while in DRAFT status.");
                            }
                            estimateRevisionRepository.deleteByEstimateId(id);
                            estimateRemarkRepository.deleteByEstimateId(id);
                            estimateRepository.delete(est);
                            log.info("Deleted estimate by Manager: id={}", id);
                            auditService.log("DELETE_ESTIMATE", "Estimate", String.valueOf(id),
                                    officerPhone, user.getName(), role,
                                    "Manager deleted draft estimate: " + est.getNameOfWork(),
                                    est.getStatus(), null, getClientIp());
                            return ResponseEntity.noContent().build();
                        })
                        .orElse(ResponseEntity.notFound().build());
                }

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .<Object>body("You do not have permission to delete this estimate.");
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found."));
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<?> performAction(
            @PathVariable(name = "id") Integer id,
            @RequestBody Map<String, String> payload) {
        String action = payload.get("action");
        String officerPhone = payload.get("officerPhone");
        String remarks = payload.get("remarks");
        String tags = payload.get("tags");

        if (action == null || officerPhone == null) {
            return ResponseEntity.badRequest().body("Action and officerPhone are required.");
        }

        Optional<User> userOpt = userRepository.findById(officerPhone.strip());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found.");
        }
        User user = userOpt.get();

        Optional<Estimate> estimateOpt = estimateRepository.findById(id);
        if (estimateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Estimate estimate = estimateOpt.get();

        String currentStatus = estimate.getStatus();
        if (currentStatus == null) currentStatus = "DRAFT";

        String role = user.getRole();
        String newStatus = null;

        if (remarks != null && !remarks.trim().isEmpty()) {
            estimate.setLastRemarks(remarks.trim());
        }

        if ("FORWARD".equalsIgnoreCase(action)) {
            if ("DRAFT".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "MANAGER")) {
                if (!user.getPhoneNumber().equals(estimate.getOfficerPhone()) && !isEstimateInOfficerJurisdiction(user, estimate) && !"ADMIN".equalsIgnoreCase(role) && !"DOP".equalsIgnoreCase(role)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Only the creator or assigned ward manager can forward this draft estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setPreparedByName(user.getName());
                estimate.setPreparedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_DGM";
            } else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "DGM")) {
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setVerifiedByName(user.getName());
                estimate.setVerifiedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_GM";
            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "GM")) {
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setRecommendedByName(user.getName());
                estimate.setRecommendedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_CGM";
            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "CGM")) {
                estimate.setStatus("SUBMITTED_TO_DOP");
                estimate.setForwardedByName(user.getName());
                estimate.setForwardedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_DOP";
            } else if ("SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "DOP")) {
                estimate.setStatus("APPROVED");
                estimate.setSanctionedByName(user.getName());
                estimate.setSanctionedByDesignation(user.getDesignation());
                newStatus = "APPROVED";
            } else {
                return ResponseEntity.badRequest().body("Invalid transition for role or status: " + currentStatus);
            }
        } else if ("RETURN".equalsIgnoreCase(action)) {
            if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "DGM")) {
                estimate.setStatus("DRAFT");
                estimate.setVerifiedByName(null);
                estimate.setVerifiedByDesignation(null);
                estimate.setRecommendedByName(null);
                estimate.setRecommendedByDesignation(null);
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                newStatus = "DRAFT";
            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "GM")) {
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setRecommendedByName(null);
                estimate.setRecommendedByDesignation(null);
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                newStatus = "SUBMITTED_TO_DGM";
            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "CGM")) {
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                newStatus = "SUBMITTED_TO_GM";
            } else if ("SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus) && hasEffectiveRoleForEstimate(user, estimate, "DOP")) {
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                newStatus = "SUBMITTED_TO_CGM";
            } else {
                return ResponseEntity.badRequest().body("Invalid transition for role or status: " + currentStatus);
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid action: " + action);
        }

        Estimate saved = estimateRepository.save(estimate);
        String details = action + " estimate: " + estimate.getNameOfWork();
        if (remarks != null && !remarks.trim().isEmpty()) {
            details += " (Remarks: " + remarks.trim() + ")";
        }
        log.info("Workflow action: {} on estimate {} by {} ({}) — {} → {}", action, id, user.getName(), role, currentStatus, newStatus);

        EstimateRevision newRev = createRevisionSnapshot(saved, user, "WORKFLOW_" + action.toUpperCase(), remarks, details);
        Integer revNum = newRev != null ? newRev.getRevisionNumber() : (int) estimateRevisionRepository.countByEstimateId(id);
        recordRemark(id, revNum, user, action.toUpperCase(), currentStatus, newStatus, remarks != null ? remarks : details, tags);

        auditService.log(action.toUpperCase(), "Estimate", String.valueOf(id),
                officerPhone, user.getName(), role,
                details,
                currentStatus, newStatus, getClientIp());
        return ResponseEntity.ok(saved);
    }

    // ── Revision Control Endpoints ───────────────────────────────────────────

    @GetMapping("/{id}/revisions")
    public ResponseEntity<?> getRevisions(@PathVariable("id") Integer id) {
        List<EstimateRevision> list = estimateRevisionRepository.findByEstimateIdOrderByRevisionNumberDesc(id);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}/revisions/{revisionNumber}")
    public ResponseEntity<?> getRevision(@PathVariable("id") Integer id,
                                         @PathVariable("revisionNumber") Integer revisionNumber) {
        return estimateRevisionRepository.findByEstimateIdAndRevisionNumber(id, revisionNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/revisions/diff")
    public ResponseEntity<?> getRevisionsDiff(@PathVariable("id") Integer id,
                                             @RequestParam("v1") Integer v1,
                                             @RequestParam("v2") Integer v2) {
        Optional<EstimateRevision> revAOpt = estimateRevisionRepository.findByEstimateIdAndRevisionNumber(id, v1);
        Optional<EstimateRevision> revBOpt = estimateRevisionRepository.findByEstimateIdAndRevisionNumber(id, v2);
        if (revAOpt.isEmpty() || revBOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("One or both revisions not found.");
        }
        Map<String, Object> diff = estimateDiffService.computeDetailedDiff(revAOpt.get(), revBOpt.get());
        return ResponseEntity.ok(diff);
    }

    @PostMapping("/{id}/revisions/{revisionNumber}/restore")
    public ResponseEntity<?> restoreRevision(@PathVariable("id") Integer id,
                                             @PathVariable("revisionNumber") Integer revisionNumber,
                                             @RequestParam("officerPhone") String officerPhone) {
        return userRepository.findById(officerPhone.strip())
            .map(user -> {
                Optional<Estimate> estOpt = estimateRepository.findById(id);
                if (estOpt.isEmpty()) return ResponseEntity.notFound().build();
                Estimate current = estOpt.get();

                String currentStatus = current.getStatus() != null ? current.getStatus() : "DRAFT";
                if (!"DRAFT".equalsIgnoreCase(currentStatus)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Can only restore historical revisions when estimate is in DRAFT status.");
                }
                if (!hasEffectiveRoleForEstimate(user, current, "MANAGER")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Only drafting Managers can restore previous revisions.");
                }

                Optional<EstimateRevision> revOpt = estimateRevisionRepository.findByEstimateIdAndRevisionNumber(id, revisionNumber);
                if (revOpt.isEmpty()) return ResponseEntity.badRequest().body("Revision not found.");

                try {
                    EstimateRevision targetRev = revOpt.get();
                    Estimate snapshotEst = objectMapper.readValue(targetRev.getSnapshotJson(), Estimate.class);

                    current.setNameOfWork(snapshotEst.getNameOfWork());
                    current.setGstPercent(snapshotEst.getGstPercent());
                    current.setUnforeseenAmount(snapshotEst.getUnforeseenAmount());
                    current.setCorp(snapshotEst.getCorp());
                    current.setZoneName(snapshotEst.getZoneName());
                    current.setDivision(snapshotEst.getDivision());
                    current.setCircleName(snapshotEst.getCircleName());
                    current.setWardName(snapshotEst.getWardName());

                    current.getItems().clear();
                    if (snapshotEst.getItems() != null) {
                        int idx = 1;
                        for (EstimateItem it : snapshotEst.getItems()) {
                            EstimateItem newItem = new EstimateItem();
                            newItem.setSno(it.getSno() != null ? it.getSno() : idx);
                            newItem.setIsMaterial(it.getIsMaterial());
                            newItem.setDescription(it.getDescription());
                            newItem.setNum(it.getNum());
                            newItem.setLength(it.getLength());
                            newItem.setBreadth(it.getBreadth());
                            newItem.setDepth(it.getDepth());
                            newItem.setQuantity(it.getQuantity());
                            newItem.setRate(it.getRate());
                            newItem.setUnit(it.getUnit());
                            newItem.setAmount(it.getAmount());
                            current.getItems().add(newItem);
                            idx++;
                        }
                    }
                    recalculateGrandTotal(current);
                    Estimate saved = estimateRepository.save(current);

                    String summary = "Restored measurements to Revision " + revisionNumber;
                    EstimateRevision newRev = createRevisionSnapshot(saved, user, "RESTORE_REVISION", "Restored to Rev " + revisionNumber, summary);
                    Integer revNum = newRev != null ? newRev.getRevisionNumber() : null;
                    recordRemark(id, revNum, user, "RESTORE", "DRAFT", "DRAFT", summary, "Restore");

                    auditService.log("RESTORE_REVISION", "Estimate", String.valueOf(saved.getId()),
                            officerPhone, user.getName(), user.getRole(),
                            summary, "DRAFT", "DRAFT", getClientIp());

                    return ResponseEntity.ok(saved);
                } catch (Exception e) {
                    log.error("Failed to restore revision: {}", e.getMessage(), e);
                    return ResponseEntity.internalServerError().body("Failed to restore revision: " + e.getMessage());
                }
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found."));
    }

    // ── Remarks Trail Endpoints ──────────────────────────────────────────────

    @GetMapping("/{id}/remarks")
    public ResponseEntity<?> getRemarks(@PathVariable("id") Integer id) {
        List<EstimateRemark> remarks = estimateRemarkRepository.findByEstimateIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(remarks);
    }

    @PostMapping("/{id}/remarks")
    public ResponseEntity<?> addRemark(@PathVariable("id") Integer id,
                                       @RequestBody Map<String, String> payload) {
        String phone = payload.get("officerPhone");
        String text = payload.get("remarks");
        String tags = payload.get("tags");
        if (phone == null || text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Officer phone and remarks text are required.");
        }
        return userRepository.findById(phone.strip())
            .map(user -> {
                Optional<Estimate> estOpt = estimateRepository.findById(id);
                if (estOpt.isEmpty()) return ResponseEntity.notFound().build();
                Estimate est = estOpt.get();

                long count = estimateRevisionRepository.countByEstimateId(id);
                EstimateRemark rem = recordRemark(id, (int) count, user, "SCRUTINY_NOTE", est.getStatus(), est.getStatus(), text.trim(), tags);
                return ResponseEntity.ok(rem);
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found."));
    }

    // ── Helper Snapshot Methods ──────────────────────────────────────────────

    private EstimateRevision createRevisionSnapshot(Estimate est, User officer, String revisionType, String remarks, String changeSummary) {
        if (est == null || est.getId() == null) return null;
        try {
            long count = estimateRevisionRepository.countByEstimateId(est.getId());
            int nextRev = (int) count + 1;

            EstimateRevision rev = new EstimateRevision();
            rev.setEstimateId(est.getId());
            rev.setRevisionNumber(nextRev);
            rev.setRevisionType(revisionType != null ? revisionType : "UPDATE");
            rev.setStatusAtRevision(est.getStatus());
            rev.setNameOfWork(est.getNameOfWork());
            rev.setGstPercent(est.getGstPercent());
            rev.setUnforeseenAmount(est.getUnforeseenAmount());
            rev.setGrandTotal(est.getGrandTotal());
            rev.setCorp(est.getCorp());
            rev.setZoneName(est.getZoneName());
            rev.setDivision(est.getDivision());
            rev.setCircleName(est.getCircleName());
            rev.setWardName(est.getWardName());

            if (officer != null) {
                rev.setOfficerPhone(officer.getPhoneNumber());
                rev.setOfficerName(officer.getName());
                rev.setOfficerRole(officer.getRole());
                rev.setOfficerDesignation(officer.getDesignation());
            } else {
                rev.setOfficerPhone(est.getOfficerPhone());
                rev.setOfficerName(est.getPreparedByName());
                rev.setOfficerRole("MANAGER");
                rev.setOfficerDesignation(est.getPreparedByDesignation());
            }

            rev.setRemarks(remarks != null ? remarks : "");
            rev.setChangeSummary(changeSummary != null ? changeSummary : "Revision " + nextRev);
            rev.setSnapshotJson(objectMapper.writeValueAsString(est));
            rev.setCreatedAt(LocalDateTime.now());

            return estimateRevisionRepository.save(rev);
        } catch (Exception e) {
            log.error("Failed to create estimate revision snapshot for estimate {}: {}", est.getId(), e.getMessage(), e);
            return null;
        }
    }

    private EstimateRemark recordRemark(Integer estimateId, Integer revisionNumber, User officer, String action, String fromStatus, String toStatus, String remarksText, String tags) {
        if (estimateId == null) return null;
        try {
            EstimateRemark rem = new EstimateRemark();
            rem.setEstimateId(estimateId);
            rem.setRevisionNumber(revisionNumber);
            if (officer != null) {
                rem.setOfficerPhone(officer.getPhoneNumber());
                rem.setOfficerName(officer.getName());
                rem.setOfficerRole(officer.getRole());
                rem.setOfficerDesignation(officer.getDesignation());
            } else {
                rem.setOfficerPhone("SYSTEM");
                rem.setOfficerName("System");
                rem.setOfficerRole("SYSTEM");
            }
            rem.setAction(action != null ? action : "COMMENT");
            rem.setFromStatus(fromStatus);
            rem.setToStatus(toStatus);
            rem.setRemarks(remarksText != null && !remarksText.trim().isEmpty() ? remarksText.trim() : action);
            rem.setTags(tags);
            rem.setCreatedAt(LocalDateTime.now());
            return estimateRemarkRepository.save(rem);
        } catch (Exception e) {
            log.error("Failed to record estimate remark for estimate {}: {}", estimateId, e.getMessage(), e);
            return null;
        }
    }

    private boolean hasEffectiveRoleForEstimate(User user, Estimate est, String requiredRole) {
        if (user == null || est == null || requiredRole == null) return false;
        String userPrimaryRole = user.getRole();

        if ("ADMIN".equalsIgnoreCase(userPrimaryRole) || "DOP".equalsIgnoreCase(userPrimaryRole)) {
            return true;
        }

        List<UserLocation> locs = user.getLocations();
        String estCorp = est.getCorp();
        String estZone = est.getZoneName();
        String estDivision = est.getDivision();
        String estCircle = est.getCircleName();
        String estWard = est.getWardName();

        if (requiredRole.equalsIgnoreCase(userPrimaryRole)) {
            if (locs == null || locs.isEmpty()) {
                return true;
            }
            for (UserLocation loc : locs) {
                if (isLocationMatchingEstimate(loc, requiredRole, estCorp, estZone, estDivision, estCircle, estWard)) {
                    return true;
                }
            }
        }

        if (locs != null) {
            for (UserLocation loc : locs) {
                String locRole = loc.getRole() != null && !loc.getRole().trim().isEmpty()
                        ? loc.getRole().toUpperCase()
                        : userPrimaryRole.toUpperCase();
                if (locRole.equalsIgnoreCase(requiredRole)) {
                    if (isLocationMatchingEstimate(loc, requiredRole, estCorp, estZone, estDivision, estCircle, estWard)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isLocationMatchingEstimate(UserLocation loc, String role,
                                               String estCorp, String estZone, String estDivision, String estCircle, String estWard) {
        if (loc == null) return false;
        String r = role.toUpperCase();

        if ("DOP".equals(r) || "ADMIN".equals(r)) {
            return true;
        }

        if ("CGM".equals(r)) {
            if (loc.getCorp() != null && !loc.getCorp().trim().isEmpty() && !safeEquals(loc.getCorp(), estCorp)) {
                return false;
            }
            if (loc.getZoneName() == null || loc.getZoneName().trim().isEmpty()) {
                return true;
            }
            return safeEquals(loc.getZoneName(), estZone);
        }

        if ("GM".equals(r)) {
            if (loc.getZoneName() != null && !loc.getZoneName().trim().isEmpty() && !safeEquals(loc.getZoneName(), estZone)) {
                return false;
            }
            if (loc.getDivision() == null || loc.getDivision().trim().isEmpty()) {
                return true;
            }
            return safeEquals(loc.getDivision(), estDivision);
        }

        if ("DGM".equals(r)) {
            if (loc.getDivision() != null && !loc.getDivision().trim().isEmpty() && !safeEquals(loc.getDivision(), estDivision)) {
                return false;
            }
            if (loc.getCircleName() == null || loc.getCircleName().trim().isEmpty()) {
                return true;
            }
            return safeEquals(loc.getCircleName(), estCircle);
        }

        if ("MANAGER".equals(r)) {
            if (loc.getCircleName() != null && !loc.getCircleName().trim().isEmpty() && !safeEquals(loc.getCircleName(), estCircle)) {
                return false;
            }
            if (loc.getWardName() == null || loc.getWardName().trim().isEmpty()) {
                return true;
            }
            return safeEquals(loc.getWardName(), estWard);
        }

        return true;
    }

    private boolean isEstimateInOfficerJurisdiction(User user, Estimate est) {
        if (user == null || est == null) return false;
        String role = user.getRole();
        if ("ADMIN".equalsIgnoreCase(role) || "DOP".equalsIgnoreCase(role)) return true;

        List<UserLocation> locs = user.getLocations();
        if (locs == null || locs.isEmpty()) return false;

        for (UserLocation loc : locs) {
            String effectiveRole = loc.getRole() != null && !loc.getRole().trim().isEmpty() ? loc.getRole() : role;
            if (isLocationMatchingEstimate(loc, effectiveRole, est.getCorp(), est.getZoneName(), est.getDivision(), est.getCircleName(), est.getWardName())) {
                return true;
            }
        }
        return false;
    }

    private void recalculateGrandTotal(Estimate estimate) {
        double rowTotal = 0.0;
        if (estimate.getItems() != null) {
            for (EstimateItem item : estimate.getItems()) {
                double qty = item.getQuantity() != null ? item.getQuantity() : 0.0;
                double rate = item.getRate() != null ? item.getRate() : 0.0;
                double amt = Math.round(qty * rate * 100.0) / 100.0;
                item.setAmount(amt);
                rowTotal += amt;
            }
        }
        double gstPct = estimate.getGstPercent() != null ? estimate.getGstPercent() : 0.0;
        double gstAmt = Math.round(rowTotal * (gstPct / 100.0) * 100.0) / 100.0;
        double unforeseen = estimate.getUnforeseenAmount() != null ? estimate.getUnforeseenAmount() : 0.0;
        double total = Math.round((rowTotal + gstAmt + unforeseen) * 100.0) / 100.0;
        estimate.setGrandTotal(total);
    }

    private String normalize(String s) {
        if (s == null) return null;
        String normalized = s
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u00A0", " ")
            .replace("\ufeff", "")
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean safeEquals(String s1, String s2) {
        String n1 = normalize(s1);
        String n2 = normalize(s2);
        if (n1 == null && n2 == null) return true;
        if (n1 == null || n2 == null) return false;
        if (n1.equalsIgnoreCase(n2)) return true;

        if (isPureNumber(n1)) {
            String num2 = extractNumberPrefix(n2);
            if (n1.equalsIgnoreCase(num2)) return true;
        }
        if (isPureNumber(n2)) {
            String num1 = extractNumberPrefix(n1);
            if (n2.equalsIgnoreCase(num1)) return true;
        }
        return false;
    }

    private boolean isPureNumber(String s) {
        if (s == null) return false;
        return s.trim().matches("^\\d+$");
    }

    private String extractNumberPrefix(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)(\\s*[-–—]\\s*.*)?$").matcher(s.trim());
        if (m.matches()) {
            return m.group(1);
        }
        return null;
    }

    private String getClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure — estimate modified by another officer");
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body("This estimate was just modified by another officer. Please refresh and try again.");
    }
}
