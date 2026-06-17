package com.hmwssb.works.controller;

import com.hmwssb.works.model.Jurisdiction;
import com.hmwssb.works.repository.JurisdictionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jurisdictions")
@CrossOrigin(origins = "*")
public class JurisdictionController {

    private final JurisdictionRepository jurisdictionRepository;

    public JurisdictionController(JurisdictionRepository jurisdictionRepository) {
        this.jurisdictionRepository = jurisdictionRepository;
    }

    @GetMapping
    public ResponseEntity<List<Jurisdiction>> getAll() {
        return ResponseEntity.ok(jurisdictionRepository.findAll());
    }
}
