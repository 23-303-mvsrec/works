package com.hmwssb.works.controller;

import com.hmwssb.works.model.Jurisdiction;
import com.hmwssb.works.repository.JurisdictionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

    @GetMapping("/hierarchy")
    public ResponseEntity<Map<String, Map<String, Map<String, Map<String, List<String>>>>>> getHierarchy() {
        List<Jurisdiction> list = jurisdictionRepository.findAll();
        Map<String, Map<String, Map<String, Map<String, List<String>>>>> hierarchy = new LinkedHashMap<>();

        for (Jurisdiction j : list) {
            String corp = j.getCorp();
            String zone = j.getZoneName();
            String div = j.getDivision();
            String circle = j.getCircleName();
            String ward = j.getWardName();

            if (corp == null || corp.trim().isEmpty()) continue;
            corp = corp.trim();
            hierarchy.putIfAbsent(corp, new LinkedHashMap<>());

            if (zone == null || zone.trim().isEmpty()) continue;
            zone = zone.trim();
            hierarchy.get(corp).putIfAbsent(zone, new LinkedHashMap<>());

            if (div == null || div.trim().isEmpty()) continue;
            div = div.trim();
            hierarchy.get(corp).get(zone).putIfAbsent(div, new LinkedHashMap<>());

            if (circle == null || circle.trim().isEmpty()) continue;
            circle = circle.trim();
            hierarchy.get(corp).get(zone).get(div).putIfAbsent(circle, new ArrayList<>());

            if (ward != null && !ward.trim().isEmpty()) {
                ward = ward.trim();
                List<String> wards = hierarchy.get(corp).get(zone).get(div).get(circle);
                if (!wards.contains(ward)) {
                    wards.add(ward);
                }
            }
        }
        return ResponseEntity.ok(hierarchy);
    }
}
