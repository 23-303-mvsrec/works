package com.hmwssb.works.controller;

import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.repository.UserRepository;
import com.hmwssb.works.repository.EstimateRepository;
import com.hmwssb.works.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final EstimateRepository estimateRepository;
    private final AuditService auditService;
    private final HttpServletRequest request;

    public UserController(UserRepository userRepository, EstimateRepository estimateRepository,
                          AuditService auditService, HttpServletRequest request) {
        this.userRepository = userRepository;
        this.estimateRepository = estimateRepository;
        this.auditService = auditService;
        this.request = request;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        log.info("Fetching all users");
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/{phoneNumber}")
    public ResponseEntity<?> getUserByPhone(@PathVariable String phoneNumber) {
        log.info("Fetching user by phone: {}", phoneNumber);
        return userRepository.findById(phoneNumber.strip())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().strip().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number is required and acts as the unique ID.");
        }
        if (user.getName() == null || user.getName().strip().isEmpty()) {
            return ResponseEntity.badRequest().body("User name is required.");
        }
        if (user.getPassword() == null || user.getPassword().strip().isEmpty()) {
            user.setPassword("1234");
        }

        user.setPhoneNumber(user.getPhoneNumber().strip());

        if (userRepository.existsById(user.getPhoneNumber())) {
            log.warn("Create user failed — phone {} already exists", user.getPhoneNumber());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A user with this phone number already exists.");
        }

        if (user.getLocations() != null) {
            user.getLocations().forEach(loc -> loc.setPhoneNumber(user.getPhoneNumber()));
        }

        User saved = userRepository.save(user);
        log.info("Created user: phone={}, name={}, role={}", saved.getPhoneNumber(), saved.getName(), saved.getRole());
        auditService.log("CREATE_USER", "User", saved.getPhoneNumber(),
                saved.getPhoneNumber(), saved.getName(), saved.getRole(),
                "Created user: " + saved.getName() + " (" + saved.getRole() + ")",
                getClientIp());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{phoneNumber}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable String phoneNumber, @RequestBody User user) {
        String oldPhone = phoneNumber.strip();
        String newPhone = user.getPhoneNumber() != null ? user.getPhoneNumber().strip() : null;

        if (newPhone == null || newPhone.isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number cannot be empty.");
        }

        java.util.Optional<User> existingOpt = userRepository.findById(oldPhone);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User existingUser = existingOpt.get();

        if (!oldPhone.equals(newPhone)) {
            if (userRepository.existsById(newPhone)) {
                log.warn("Update user failed — new phone {} already exists", newPhone);
                return ResponseEntity.status(HttpStatus.CONFLICT).body("A user with the new phone number already exists.");
            }

            User newUser = new User();
            newUser.setPhoneNumber(newPhone);
            newUser.setName(user.getName());
            newUser.setPassword(user.getPassword() != null && !user.getPassword().isEmpty() ? user.getPassword() : existingUser.getPassword());
            newUser.setDesignation(user.getDesignation());
            newUser.setRole(user.getRole());

            List<UserLocation> newLocs = new ArrayList<>();
            if (user.getLocations() != null) {
                for (UserLocation loc : user.getLocations()) {
                    UserLocation newLoc = new UserLocation();
                    newLoc.setCorp(loc.getCorp());
                    newLoc.setZoneName(loc.getZoneName());
                    newLoc.setDivision(loc.getDivision());
                    newLoc.setCircleName(loc.getCircleName());
                    newLoc.setWardName(loc.getWardName());
                    newLoc.setRole(loc.getRole());
                    newLoc.setPhoneNumber(newPhone);
                    newLocs.add(newLoc);
                }
            }
            newUser.setLocations(newLocs);

            userRepository.delete(existingUser);
            userRepository.flush();

            User saved = userRepository.save(newUser);
            estimateRepository.updateOfficerPhone(oldPhone, newPhone);

            log.info("Updated user: phone changed {} → {}", oldPhone, newPhone);
            auditService.log("UPDATE_USER", "User", newPhone,
                    newPhone, saved.getName(), saved.getRole(),
                    "Phone changed: " + oldPhone + " → " + newPhone,
                    getClientIp());
            return ResponseEntity.ok(saved);
        } else {
            existingUser.setName(user.getName());
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                existingUser.setPassword(user.getPassword());
            }
            existingUser.setDesignation(user.getDesignation());
            existingUser.setRole(user.getRole());

            existingUser.getLocations().clear();
            if (user.getLocations() != null) {
                for (UserLocation loc : user.getLocations()) {
                    UserLocation newLoc = new UserLocation();
                    newLoc.setCorp(loc.getCorp());
                    newLoc.setZoneName(loc.getZoneName());
                    newLoc.setDivision(loc.getDivision());
                    newLoc.setCircleName(loc.getCircleName());
                    newLoc.setWardName(loc.getWardName());
                    newLoc.setRole(loc.getRole());
                    newLoc.setPhoneNumber(oldPhone);
                    existingUser.getLocations().add(newLoc);
                }
            }

            User saved = userRepository.save(existingUser);
            log.info("Updated user: phone={}, name={}, role={}", oldPhone, saved.getName(), saved.getRole());
            auditService.log("UPDATE_USER", "User", oldPhone,
                    oldPhone, saved.getName(), saved.getRole(),
                    "Updated user details: " + saved.getName(),
                    getClientIp());
            return ResponseEntity.ok(saved);
        }
    }

    @DeleteMapping("/{phoneNumber}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable String phoneNumber) {
        String phone = phoneNumber.strip();
        return userRepository.findById(phone)
                .map(user -> {
                    String name = user.getName();
                    String role = user.getRole();
                    userRepository.delete(user);
                    log.info("Deleted user: phone={}, name={}, role={}", phone, name, role);
                    auditService.log("DELETE_USER", "User", phone,
                            phone, name, role,
                            "Deleted user: " + name,
                            getClientIp());
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String phoneNumber = credentials.get("phoneNumber");
        String password = credentials.get("password");

        if (phoneNumber == null || phoneNumber.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number and password are required.");
        }

        return userRepository.findById(phoneNumber.strip())
                .map(user -> {
                    if (user.getPassword().equals(password)) {
                        log.info("Login successful: phone={}, name={}, role={}", phoneNumber, user.getName(), user.getRole());
                        auditService.log("LOGIN", "User", phoneNumber,
                                phoneNumber, user.getName(), user.getRole(),
                                "Login successful",
                                getClientIp());
                        return ResponseEntity.ok(user);
                    } else {
                        log.warn("Login failed (wrong password): phone={}", phoneNumber);
                        auditService.log("LOGIN_FAILED", "User", phoneNumber,
                                phoneNumber, null, null,
                                "Login failed — wrong password",
                                getClientIp());
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid phone number or password.");
                    }
                })
                .orElseGet(() -> {
                    log.warn("Login failed (user not found): phone={}", phoneNumber);
                    auditService.log("LOGIN_FAILED", "User", phoneNumber,
                            phoneNumber, null, null,
                            "Login failed — user not found",
                            getClientIp());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User account not found.");
                });
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().strip().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number is required and acts as the unique ID.");
        }
        if (user.getName() == null || user.getName().strip().isEmpty()) {
            return ResponseEntity.badRequest().body("User name is required.");
        }
        if (user.getPassword() == null || user.getPassword().strip().isEmpty()) {
            user.setPassword("1234");
        }

        user.setPhoneNumber(user.getPhoneNumber().strip());

        if (userRepository.existsById(user.getPhoneNumber())) {
            log.warn("Register failed — phone {} already exists", user.getPhoneNumber());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A user with this phone number already exists.");
        }

        if (user.getLocations() != null) {
            user.getLocations().forEach(loc -> loc.setPhoneNumber(user.getPhoneNumber()));
        }

        User saved = userRepository.save(user);
        log.info("Registered user: phone={}, name={}, role={}", saved.getPhoneNumber(), saved.getName(), saved.getRole());
        auditService.log("REGISTER", "User", saved.getPhoneNumber(),
                saved.getPhoneNumber(), saved.getName(), saved.getRole(),
                "Registered new user: " + saved.getName(),
                getClientIp());
        return ResponseEntity.ok(saved);
    }

    private String getClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
