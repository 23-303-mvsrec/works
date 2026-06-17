package com.hmwssb.works.controller;

import com.hmwssb.works.model.Estimate;
import com.hmwssb.works.model.EstimateItem;
import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.repository.EstimateRepository;
import com.hmwssb.works.repository.UserRepository;
import com.hmwssb.works.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estimates")
@CrossOrigin(origins = "*")
public class EstimateController {

    private static final Logger log = LoggerFactory.getLogger(EstimateController.class);

    private final EstimateRepository estimateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final HttpServletRequest request;

    public EstimateController(EstimateRepository estimateRepository, UserRepository userRepository,
                              AuditService auditService, HttpServletRequest request) {
        this.estimateRepository = estimateRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.request = request;
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
                if (estimate.getId() == null) {
                    if (!"MANAGER".equalsIgnoreCase(role)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only MANAGER can create a new estimate.");
                    }
                    estimate.setStatus("DRAFT");
                    estimate.setPreparedByName(user.getName());
                    estimate.setPreparedByDesignation(user.getDesignation());
                    recalculateGrandTotal(estimate);
                    Estimate saved = estimateRepository.save(estimate);
                    log.info("Created estimate: id={}, officer={}, nameOfWork={}", saved.getId(), phone, saved.getNameOfWork());
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

                            boolean allowed = false;
                            if ("DRAFT".equalsIgnoreCase(currentStatus) && "MANAGER".equalsIgnoreCase(role)) {
                                allowed = true;
                            } else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus) && "DGM".equalsIgnoreCase(role)) {
                                allowed = true;
                            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus) && "GM".equalsIgnoreCase(role)) {
                                allowed = true;
                            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus) && "CGM".equalsIgnoreCase(role)) {
                                allowed = true;
                            }

                            if (!allowed) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body("You do not have permission to edit this estimate at its current status: " + currentStatus);
                            }

                            existing.setNameOfWork(estimate.getNameOfWork());
                            existing.setGstPercent(estimate.getGstPercent());
                            existing.setUnforeseenAmount(estimate.getUnforeseenAmount());
                            existing.setCorp(estimate.getCorp());
                            existing.setZoneName(estimate.getZoneName());
                            existing.setDivision(estimate.getDivision());
                            existing.setCircleName(estimate.getCircleName());
                            existing.setWardName(estimate.getWardName());

                            existing.getItems().clear();
                            if (estimate.getItems() != null) {
                                for (EstimateItem item : estimate.getItems()) {
                                    item.setId(null);
                                    item.setSno(item.getSno());
                                    existing.getItems().add(item);
                                }
                            }

                            recalculateGrandTotal(existing);
                            Estimate saved = estimateRepository.save(existing);
                            log.info("Updated estimate: id={}, officer={}, status={}", saved.getId(), phone, currentStatus);
                            auditService.log("UPDATE_ESTIMATE", "Estimate", String.valueOf(saved.getId()),
                                    phone, user.getName(), role,
                                    "Updated estimate: " + saved.getNameOfWork(),
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
    public ResponseEntity<List<Estimate>> list(
            @RequestParam(name = "officerPhone", required = false) String officerPhone,
            @RequestParam(name = "wardName", required = false) String wardName,
            @RequestParam(name = "circleName", required = false) String circleName,
            @RequestParam(name = "division", required = false) String division,
            @RequestParam(name = "status", required = false) String status) {
        if (officerPhone == null || officerPhone.trim().isEmpty()) {
            return ResponseEntity.ok(estimateRepository.findAll());
        }

        return userRepository.findById(officerPhone.strip())
                .map(user -> {
                    List<Estimate> all = estimateRepository.findAll();
                    String role = user.getRole();
                    List<UserLocation> locs = user.getLocations();

                    if ("ADMIN".equalsIgnoreCase(role)) {
                        List<Estimate> filtered = all;
                        if (status != null && !status.trim().isEmpty()) {
                            filtered = filtered.stream()
                                .filter(e -> status.equalsIgnoreCase(e.getStatus()))
                                .toList();
                        }
                        return ResponseEntity.ok(filtered);
                    }

                    List<Estimate> filtered = all.stream().filter(est -> {
                        return matchEstimateToRole(user, est, locs, wardName, circleName, division);
                    }).toList();

                    if (status != null && !status.trim().isEmpty()) {
                        filtered = filtered.stream()
                            .filter(e -> status.equalsIgnoreCase(e.getStatus()))
                            .toList();
                    }

                    return ResponseEntity.ok(filtered);
                })
                .orElseGet(() -> ResponseEntity.ok(estimateRepository.findAll()));
    }

    private boolean matchEstimateToRole(User user, Estimate est, List<UserLocation> locs,
                                         String wardFilter, String circleFilter, String divisionFilter) {
        String role = user.getRole();
        String estCorp = est.getCorp();
        String estZone = est.getZoneName();
        String estDivision = est.getDivision();
        String estCircle = est.getCircleName();
        String estWard = est.getWardName();

        if ("DOP".equalsIgnoreCase(role)) {
            boolean inScope = locs.stream().anyMatch(loc ->
                safeEquals(loc.getCorp(), estCorp) &&
                safeEquals(loc.getZoneName(), estZone)
            );
            if (!inScope) return false;
            if (divisionFilter != null && !divisionFilter.trim().isEmpty()) {
                return safeEquals(divisionFilter, estDivision);
            }
            if (circleFilter != null && !circleFilter.trim().isEmpty()) {
                return safeEquals(circleFilter, estCircle);
            }
            if (wardFilter != null && !wardFilter.trim().isEmpty()) {
                return safeEquals(wardFilter, estWard);
            }
            return true;
        }

        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }

        if ("MANAGER".equalsIgnoreCase(role)) {
            if (user.getPhoneNumber().equals(est.getOfficerPhone())) {
                return true;
            }
            if (wardFilter != null && !wardFilter.trim().isEmpty()) {
                return locs.stream().anyMatch(loc ->
                    safeEquals(loc.getCorp(), estCorp) &&
                    safeEquals(loc.getZoneName(), estZone) &&
                    safeEquals(loc.getDivision(), estDivision) &&
                    safeEquals(loc.getCircleName(), estCircle) &&
                    wardFilter.trim().equalsIgnoreCase(estWard)
                );
            }
            return false;
        }

        if ("DGM".equalsIgnoreCase(role)) {
            String userCircle = locs.stream()
                .map(UserLocation::getCircleName)
                .filter(c -> c != null && !c.trim().isEmpty())
                .findFirst()
                .orElse(null);
            if (userCircle != null) {
                if (!safeEquals(userCircle, estCircle)) return false;
            } else {
                boolean inZone = locs.stream().anyMatch(loc ->
                    safeEquals(loc.getCorp(), estCorp) &&
                    safeEquals(loc.getZoneName(), estZone)
                );
                if (!inZone) return false;
            }
            if (wardFilter != null && !wardFilter.trim().isEmpty()) {
                return safeEquals(wardFilter, estWard);
            }
            return true;
        }

        if ("GM".equalsIgnoreCase(role)) {
            boolean inDivision = locs.stream().anyMatch(loc ->
                safeEquals(loc.getCorp(), estCorp) &&
                safeEquals(loc.getZoneName(), estZone) &&
                safeEquals(loc.getDivision(), estDivision)
            );
            if (!inDivision) return false;
            if (circleFilter != null && !circleFilter.trim().isEmpty()) {
                return safeEquals(circleFilter, estCircle);
            }
            if (wardFilter != null && !wardFilter.trim().isEmpty()) {
                return safeEquals(wardFilter, estWard);
            }
            return true;
        }

        if ("CGM".equalsIgnoreCase(role)) {
            boolean inZone = locs.stream().anyMatch(loc ->
                safeEquals(loc.getCorp(), estCorp) &&
                safeEquals(loc.getZoneName(), estZone)
            );
            if (!inZone) return false;
            if (divisionFilter != null && !divisionFilter.trim().isEmpty()) {
                return safeEquals(divisionFilter, estDivision);
            }
            if (circleFilter != null && !circleFilter.trim().isEmpty()) {
                return safeEquals(circleFilter, estCircle);
            }
            if (wardFilter != null && !wardFilter.trim().isEmpty()) {
                return safeEquals(wardFilter, estWard);
            }
            return true;
        }

        return false;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Estimate> get(@PathVariable(name = "id") Integer id) {
        log.info("Fetching estimate: id={}", id);
        return estimateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable(name = "id") Integer id,
            @RequestParam(name = "officerPhone", required = false) String officerPhone) {
        if (!estimateRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        if (officerPhone == null || officerPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("officerPhone is required.");
        }

        return userRepository.findById(officerPhone.strip())
            .map(user -> {
                String role = user.getRole();
                if ("DOP".equalsIgnoreCase(role)) {
                    estimateRepository.deleteById(id);
                    log.info("Deleted estimate: id={} by DOP {}", id, officerPhone);
                    auditService.log("DELETE_ESTIMATE", "Estimate", String.valueOf(id),
                            officerPhone, user.getName(), role,
                            "Deleted estimate (DOP)",
                            null, null, getClientIp());
                    return ResponseEntity.ok().<Void>build();
                }

                if ("MANAGER".equalsIgnoreCase(role)) {
                    return estimateRepository.findById(id)
                        .map(est -> {
                            if (user.getPhoneNumber().equals(est.getOfficerPhone()) && "DRAFT".equalsIgnoreCase(est.getStatus())) {
                                estimateRepository.deleteById(id);
                                log.info("Deleted estimate: id={} by MANAGER {} (own draft)", id, officerPhone);
                                auditService.log("DELETE_ESTIMATE", "Estimate", String.valueOf(id),
                                        officerPhone, user.getName(), role,
                                        "Deleted own draft estimate: " + est.getNameOfWork(),
                                        "DRAFT", null, getClientIp());
                                return ResponseEntity.ok().<Void>build();
                            }
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Object>body("Only the creator can delete a DRAFT estimate.");
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

        if (action == null || officerPhone == null) {
            return ResponseEntity.badRequest().body("Action and officerPhone are required.");
        }

        java.util.Optional<User> userOpt = userRepository.findById(officerPhone.strip());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found.");
        }
        User user = userOpt.get();

        java.util.Optional<Estimate> estimateOpt = estimateRepository.findById(id);
        if (estimateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Estimate estimate = estimateOpt.get();

        String currentStatus = estimate.getStatus();
        if (currentStatus == null) currentStatus = "DRAFT";

        String role = user.getRole();
        String newStatus = null;

        if ("FORWARD".equalsIgnoreCase(action)) {
            if ("MANAGER".equalsIgnoreCase(role) && "DRAFT".equalsIgnoreCase(currentStatus)) {
                if (!user.getPhoneNumber().equals(estimate.getOfficerPhone())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Only the creator can forward this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setPreparedByName(user.getName());
                estimate.setPreparedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_DGM";
            } else if ("DGM".equalsIgnoreCase(role) && "SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                if (!isEstimateInScope(user, estimate)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This estimate is not within your geographic scope.");
                }
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setVerifiedByName(user.getName());
                estimate.setVerifiedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_GM";
            } else if ("GM".equalsIgnoreCase(role) && "SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                if (!isEstimateInScope(user, estimate)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This estimate is not within your geographic scope.");
                }
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setRecommendedByName(user.getName());
                estimate.setRecommendedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_CGM";
            } else if ("CGM".equalsIgnoreCase(role) && "SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                if (!isEstimateInScope(user, estimate)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This estimate is not within your geographic scope.");
                }
                estimate.setStatus("SUBMITTED_TO_DOP");
                estimate.setForwardedByName(user.getName());
                estimate.setForwardedByDesignation(user.getDesignation());
                newStatus = "SUBMITTED_TO_DOP";
            } else if ("DOP".equalsIgnoreCase(role) && "SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("APPROVED");
                estimate.setSanctionedByName(user.getName());
                estimate.setSanctionedByDesignation(user.getDesignation());
                newStatus = "APPROVED";
            } else {
                return ResponseEntity.badRequest().body("Invalid transition for role " + role + " and status " + currentStatus);
            }
        } else if ("RETURN".equalsIgnoreCase(action)) {
            if ("DGM".equalsIgnoreCase(role) && "SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                if (!isEstimateInScope(user, estimate)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This estimate is not within your geographic scope.");
                }
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
            } else if ("GM".equalsIgnoreCase(role) && "SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                if (!isEstimateInScope(user, estimate)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This estimate is not within your geographic scope.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setRecommendedByName(null);
                estimate.setRecommendedByDesignation(null);
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                newStatus = "SUBMITTED_TO_DGM";
            } else if ("CGM".equalsIgnoreCase(role) && "SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                if (!isEstimateInScope(user, estimate)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This estimate is not within your geographic scope.");
                }
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                newStatus = "SUBMITTED_TO_GM";
            } else if ("DOP".equalsIgnoreCase(role) && "SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
                newStatus = "SUBMITTED_TO_CGM";
            } else {
                return ResponseEntity.badRequest().body("Invalid transition for role " + role + " and status " + currentStatus);
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid action: " + action);
        }

        Estimate saved = estimateRepository.save(estimate);
        log.info("Workflow action: {} on estimate {} by {} ({}) — {} → {}", action, id, user.getName(), role, currentStatus, newStatus);
        auditService.log(action.toUpperCase(), "Estimate", String.valueOf(id),
                officerPhone, user.getName(), role,
                action + " estimate: " + estimate.getNameOfWork(),
                currentStatus, newStatus, getClientIp());
        return ResponseEntity.ok(saved);
    }

    private boolean isEstimateInScope(User user, Estimate est) {
        String role = user.getRole();
        List<UserLocation> locs = user.getLocations();
        String estCorp = est.getCorp();
        String estZone = est.getZoneName();
        String estDivision = est.getDivision();
        String estCircle = est.getCircleName();

        if ("DGM".equalsIgnoreCase(role)) {
            String userCircle = locs.stream()
                .map(UserLocation::getCircleName)
                .filter(c -> c != null && !c.trim().isEmpty())
                .findFirst()
                .orElse(null);
            if (userCircle != null) {
                return safeEquals(userCircle, estCircle);
            }
            return locs.stream().anyMatch(loc ->
                safeEquals(loc.getCorp(), estCorp) &&
                safeEquals(loc.getZoneName(), estZone)
            );
        }
        if ("GM".equalsIgnoreCase(role)) {
            return locs.stream().anyMatch(loc ->
                safeEquals(loc.getCorp(), estCorp) &&
                safeEquals(loc.getZoneName(), estZone) &&
                safeEquals(loc.getDivision(), estDivision)
            );
        }
        if ("CGM".equalsIgnoreCase(role)) {
            return locs.stream().anyMatch(loc ->
                safeEquals(loc.getCorp(), estCorp) &&
                safeEquals(loc.getZoneName(), estZone)
            );
        }
        return true;
    }

    private void recalculateGrandTotal(Estimate estimate) {
        double totalAmount = 0.0;
        if (estimate.getItems() != null) {
            for (EstimateItem item : estimate.getItems()) {
                totalAmount += (item.getAmount() != null) ? item.getAmount() : 0.0;
            }
        }
        double gstPercent = (estimate.getGstPercent() != null) ? estimate.getGstPercent() : 0.0;
        double gstAmount = totalAmount * gstPercent / 100.0;
        double unforeseen = (estimate.getUnforeseenAmount() != null) ? estimate.getUnforeseenAmount() : 0.0;
        estimate.setGrandTotal(totalAmount + gstAmount + unforeseen);
    }

    private boolean safeEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
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
