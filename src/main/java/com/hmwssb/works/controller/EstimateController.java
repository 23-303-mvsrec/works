package com.hmwssb.works.controller;

import com.hmwssb.works.model.Estimate;
import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.repository.EstimateRepository;
import com.hmwssb.works.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estimates")
@CrossOrigin(origins = "*")
public class EstimateController {

    private final EstimateRepository estimateRepository;
    private final UserRepository userRepository;

    public EstimateController(EstimateRepository estimateRepository, UserRepository userRepository) {
        this.estimateRepository = estimateRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Estimate estimate) {
        String phone = estimate.getOfficerPhone();
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Officer phone is required.");
        }

        return userRepository.findById(phone.strip())
            .map(user -> {
                if (estimate.getId() == null) {
                    if (!hasRoleAtLocation(user, "MANAGER", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a MANAGER assigned to this ward can create this estimate.");
                    }
                    estimate.setStatus("DRAFT");
                    estimate.setPreparedByName(user.getName());
                    estimate.setPreparedByDesignation(user.getDesignation());
                    Estimate saved = estimateRepository.save(estimate);
                    return ResponseEntity.ok(saved);
                } else {
                    return estimateRepository.findById(estimate.getId())
                        .map(existing -> {
                            String currentStatus = existing.getStatus();
                            if (currentStatus == null) currentStatus = "DRAFT";

                            String requiredRole = null;
                            if ("DRAFT".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "MANAGER";
                            } else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "DGM";
                            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "GM";
                            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "CGM";
                            }

                            if (requiredRole == null || !hasRoleAtLocation(user, requiredRole, existing.getCorp(), existing.getZoneName(), existing.getDivision(), existing.getCircleName(), existing.getWardName())) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body("You do not have permission to edit this estimate at its current status: " + currentStatus);
                            }

                            existing.setNameOfWork(estimate.getNameOfWork());
                            existing.setGstPercent(estimate.getGstPercent());
                            existing.setUnforeseenAmount(estimate.getUnforeseenAmount());
                            existing.setGrandTotal(estimate.getGrandTotal());
                            existing.setCorp(estimate.getCorp());
                            existing.setZoneName(estimate.getZoneName());
                            existing.setDivision(estimate.getDivision());
                            existing.setCircleName(estimate.getCircleName());
                            existing.setWardName(estimate.getWardName());

                            existing.getItems().clear();
                            if (estimate.getItems() != null) {
                                existing.getItems().addAll(estimate.getItems());
                            }

                            Estimate saved = estimateRepository.save(existing);
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
            @RequestParam(name = "role", required = false) String activeRole,
            @RequestParam(name = "corp", required = false) String corp,
            @RequestParam(name = "zoneName", required = false) String zoneName,
            @RequestParam(name = "division", required = false) String division,
            @RequestParam(name = "circleName", required = false) String circleName,
            @RequestParam(name = "wardName", required = false) String wardName) {

        if (officerPhone == null || officerPhone.trim().isEmpty()) {
            return ResponseEntity.ok(estimateRepository.findAll());
        }

        java.util.Optional<User> userOpt = userRepository.findById(officerPhone.strip());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found.");
        }
        User user = userOpt.get();

        if (activeRole == null || activeRole.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        boolean hasAssignment = hasRoleAtLocation(user, activeRole, corp, zoneName, division, circleName, wardName);

        if (!hasAssignment) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have access to this location/role scope.");
        }

        List<Estimate> all = estimateRepository.findAll();
        List<Estimate> filtered = all.stream().filter(est -> {
            if ("DOP".equalsIgnoreCase(activeRole)) {
                return true;
            }
            if ("CGM".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName());
            }
            if ("GM".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName()) && safeEquals(division, est.getDivision());
            }
            if ("DGM".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName()) && safeEquals(division, est.getDivision()) && safeEquals(circleName, est.getCircleName());
            }
            if ("MANAGER".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName()) && safeEquals(division, est.getDivision()) && safeEquals(circleName, est.getCircleName()) && safeEquals(wardName, est.getWardName());
            }
            return false;
        }).toList();

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Estimate> get(@PathVariable(name = "id") Integer id) {
        return estimateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable(name = "id") Integer id) {
        if (estimateRepository.existsById(id)) {
            estimateRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<?> performAction(
            @PathVariable(name = "id") Integer id,
            @RequestBody Map<String, String> payload) {
        String action = payload.get("action"); // "FORWARD" or "RETURN"
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

        if ("FORWARD".equalsIgnoreCase(action)) {
            if ("DRAFT".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "MANAGER", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a MANAGER for this ward can forward this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setPreparedByName(user.getName());
                estimate.setPreparedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DGM for this circle can verify this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setVerifiedByName(user.getName());
                estimate.setVerifiedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "GM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a GM for this division can recommend this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setRecommendedByName(user.getName());
                estimate.setRecommendedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "CGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a CGM for this zone can forward this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DOP");
                estimate.setForwardedByName(user.getName());
                estimate.setForwardedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DOP", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DOP can approve/sanction this estimate.");
                }
                estimate.setStatus("APPROVED");
                estimate.setSanctionedByName(user.getName());
                estimate.setSanctionedByDesignation(user.getDesignation());
            } else {
                return ResponseEntity.badRequest().body("Invalid forward transition for status " + currentStatus);
            }
        } else if ("RETURN".equalsIgnoreCase(action)) {
            if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DGM for this circle can return this estimate.");
                }
                estimate.setStatus("DRAFT");
                estimate.setVerifiedByName(null);
                estimate.setVerifiedByDesignation(null);
            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "GM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a GM for this division can return this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setRecommendedByName(null);
                estimate.setRecommendedByDesignation(null);
            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "CGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a CGM for this zone can return this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
            } else if ("SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DOP", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DOP can return this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
            } else {
                return ResponseEntity.badRequest().body("Invalid return transition for status " + currentStatus);
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid action: " + action);
        }

        Estimate saved = estimateRepository.save(estimate);
        return ResponseEntity.ok(saved);
    }

    private boolean hasRoleAtLocation(User user, String requiredRole, String corp, String zoneName, String division, String circleName, String wardName) {
        return user.getLocations().stream().anyMatch(loc -> {
            if (!safeEquals(loc.getRole(), requiredRole)) return false;
            if ("DOP".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), "Corporate");
            }
            if ("CGM".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName);
            }
            if ("GM".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName) && safeEquals(loc.getDivision(), division);
            }
            if ("DGM".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName) && safeEquals(loc.getDivision(), division) && safeEquals(loc.getCircleName(), circleName);
            }
            if ("MANAGER".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName) && safeEquals(loc.getDivision(), division) && safeEquals(loc.getCircleName(), circleName) && safeEquals(loc.getWardName(), wardName);
            }
            return false;
        });
    }

    private boolean safeEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }
}
