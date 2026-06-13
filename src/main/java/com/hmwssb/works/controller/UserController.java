package com.hmwssb.works.controller;

import com.hmwssb.works.model.User;
import com.hmwssb.works.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
                        return ResponseEntity.ok(user);
                    } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid phone number or password.");
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User account not found."));
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
            user.setPassword("12345678"); // Default password
        }

        // Standardize phone number format
        user.setPhoneNumber(user.getPhoneNumber().strip());

        if (userRepository.existsById(user.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A user with this phone number already exists.");
        }

        // Set phone number relation back in locations if not set
        if (user.getLocations() != null) {
            user.getLocations().forEach(loc -> loc.setPhoneNumber(user.getPhoneNumber()));
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }
}
