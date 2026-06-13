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
                String role = user.getRole();
                if (estimate.getId() == null) {
                    if (!"MANAGER".equalsIgnoreCase(role)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only MANAGER can create a new estimate.");
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
    public ResponseEntity<List<Estimate>> list(@RequestParam(name = "officerPhone", required = false) String officerPhone) {
        if (officerPhone == null || officerPhone.trim().isEmpty()) {
            return ResponseEntity.ok(estimateRepository.findAll());
        }

        return userRepository.findById(officerPhone.strip())
                .map(user -> {
                    List<Estimate> all = estimateRepository.findAll();
                    String role = user.getRole();
                    if ("DOP".equalsIgnoreCase(role)) {
                        return ResponseEntity.ok(all);
                    }

                    List<UserLocation> locs = user.getLocations();
                    List<Estimate> filtered = all.stream().filter(est -> {
                        if ("MANAGER".equalsIgnoreCase(role)) {
                            if (user.getPhoneNumber().equals(est.getOfficerPhone())) {
                                return true;
                            }
                            return locs.stream().anyMatch(loc ->
                                safeEquals(loc.getCorp(), est.getCorp()) &&
                                safeEquals(loc.getZoneName(), est.getZoneName()) &&
                                safeEquals(loc.getDivision(), est.getDivision()) &&
                                safeEquals(loc.getCircleName(), est.getCircleName()) &&
                                safeEquals(loc.getWardName(), est.getWardName())
                            );
                        } else if ("DGM".equalsIgnoreCase(role) || "GM".equalsIgnoreCase(role)) {
                            return locs.stream().anyMatch(loc ->
                                safeEquals(loc.getCorp(), est.getCorp()) &&
                                safeEquals(loc.getZoneName(), est.getZoneName()) &&
                                safeEquals(loc.getDivision(), est.getDivision())
                            );
                        } else if ("CGM".equalsIgnoreCase(role)) {
                            return locs.stream().anyMatch(loc ->
                                safeEquals(loc.getCorp(), est.getCorp()) &&
                                safeEquals(loc.getZoneName(), est.getZoneName())
                            );
                        }
                        return false;
                    }).toList();

                    return ResponseEntity.ok(filtered);
                })
                .orElseGet(() -> ResponseEntity.ok(estimateRepository.findAll()));
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

        String role = user.getRole();
        if ("FORWARD".equalsIgnoreCase(action)) {
            if ("MANAGER".equalsIgnoreCase(role) && "DRAFT".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setPreparedByName(user.getName());
                estimate.setPreparedByDesignation(user.getDesignation());
            } else if ("DGM".equalsIgnoreCase(role) && "SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setVerifiedByName(user.getName());
                estimate.setVerifiedByDesignation(user.getDesignation());
            } else if ("GM".equalsIgnoreCase(role) && "SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setRecommendedByName(user.getName());
                estimate.setRecommendedByDesignation(user.getDesignation());
            } else if ("CGM".equalsIgnoreCase(role) && "SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_DOP");
                estimate.setForwardedByName(user.getName());
                estimate.setForwardedByDesignation(user.getDesignation());
            } else if ("DOP".equalsIgnoreCase(role) && "SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("APPROVED");
                estimate.setSanctionedByName(user.getName());
                estimate.setSanctionedByDesignation(user.getDesignation());
            } else {
                return ResponseEntity.badRequest().body("Invalid transition for role " + role + " and status " + currentStatus);
            }
        } else if ("RETURN".equalsIgnoreCase(action)) {
            if ("DGM".equalsIgnoreCase(role) && "SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("DRAFT");
                estimate.setVerifiedByName(null);
                estimate.setVerifiedByDesignation(null);
            } else if ("GM".equalsIgnoreCase(role) && "SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setRecommendedByName(null);
                estimate.setRecommendedByDesignation(null);
            } else if ("CGM".equalsIgnoreCase(role) && "SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
            } else if ("DOP".equalsIgnoreCase(role) && "SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
            } else {
                return ResponseEntity.badRequest().body("Invalid transition for role " + role + " and status " + currentStatus);
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid action: " + action);
        }

        Estimate saved = estimateRepository.save(estimate);
        return ResponseEntity.ok(saved);
    }

    private boolean safeEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }
}
