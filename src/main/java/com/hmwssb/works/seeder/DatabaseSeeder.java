package com.hmwssb.works.seeder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmwssb.works.model.*;
import com.hmwssb.works.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final UserRepository userRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final EstimateRepository estimateRepository;
    private final EstimateRevisionRepository estimateRevisionRepository;
    private final EstimateRemarkRepository estimateRemarkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DatabaseSeeder(UserRepository userRepository,
                          JurisdictionRepository jurisdictionRepository,
                          EstimateRepository estimateRepository,
                          EstimateRevisionRepository estimateRevisionRepository,
                          EstimateRemarkRepository estimateRemarkRepository,
                          JdbcTemplate jdbcTemplate,
                          ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.estimateRepository = estimateRepository;
        this.estimateRevisionRepository = estimateRevisionRepository;
        this.estimateRemarkRepository = estimateRemarkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        ObjectMapper mapper = this.objectMapper;

        // 0. Fix NULL version column on existing estimates (causes NPE on save)
        try {
            int fixed = jdbcTemplate.update("UPDATE estimates SET version = 0 WHERE version IS NULL");
            if (fixed > 0) {
                log.warn("Fixed {} estimates with NULL version column", fixed);
            }
        } catch (Exception e) {
            log.info("Version column fix skipped: {}", e.getMessage());
        }

        // 0b. Fix corrupted circle names in jurisdictions and estimates (encoding issue from Excel export)
        try {
            jdbcTemplate.update("UPDATE estimates SET " +
                "circle_name = REPLACE(REPLACE(REPLACE(circle_name, CHR(8211), '-'), CHR(8212), '-'), CHR(160), ' '), " +
                "ward_name = REPLACE(REPLACE(REPLACE(ward_name, CHR(8211), '-'), CHR(8212), '-'), CHR(160), ' '), " +
                "zone_name = REPLACE(REPLACE(REPLACE(zone_name, CHR(8211), '-'), CHR(8212), '-'), CHR(160), ' ')");

            Integer corruptedJurs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jurisdictions WHERE circle_name ~ '[^[:ascii:]]'",
                Integer.class);
            if (corruptedJurs != null && corruptedJurs > 0) {
                log.warn("Found {} jurisdictions with corrupted circle names. Re-seeding...", corruptedJurs);
                jurisdictionRepository.deleteAll();
            }
        } catch (Exception e) {
            log.info("Jurisdiction encoding check skipped: {}", e.getMessage());
        }

        // 1. Seed Jurisdictions
        ClassPathResource jurResource = new ClassPathResource("jurisdictions.json");
        if (jurisdictionRepository.count() == 0 && jurResource.exists()) {
            log.info("Seeding jurisdictions from jurisdictions.json...");
            try (InputStream is = jurResource.getInputStream()) {
                List<Map<String, Object>> rawJurs = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
                List<Jurisdiction> jursToSave = new ArrayList<>();
                for (Map<String, Object> rawJur : rawJurs) {
                    Jurisdiction jur = new Jurisdiction();
                    jur.setCorp(cleanString((String) rawJur.get("corp")));
                    jur.setZoneName(cleanString((String) rawJur.get("zoneName")));
                    jur.setDivision(cleanString((String) rawJur.get("division")));
                    jur.setCircleName(cleanString((String) rawJur.get("circleName")));
                    jur.setWardName(cleanString((String) rawJur.get("wardName")));
                    jursToSave.add(jur);
                }
                jurisdictionRepository.saveAll(jursToSave);
                log.info("Successfully seeded {} jurisdictions from Excel data", jursToSave.size());
            } catch (Exception e) {
                log.error("Error seeding jurisdictions: {}", e.getMessage(), e);
            }
        }

        // 2. Seed Users
        ClassPathResource resource = new ClassPathResource("users_parsed.json");
        if (!resource.exists()) {
            log.error("Could not find users_parsed.json in classpath resources!");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Map<String, Object>> rawUsers = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            int addedCount = 0;

            for (Map<String, Object> rawUser : rawUsers) {
                String phone = cleanString((String) rawUser.get("phoneNumber"));
                if (phone == null || phone.isEmpty()) continue;

                User user = userRepository.findById(phone).orElseGet(() -> {
                    User u = new User();
                    u.setPhoneNumber(phone);
                    u.setPassword("1234");
                    return u;
                });

                user.setName(cleanString((String) rawUser.get("name")));
                user.setDesignation(cleanString((String) rawUser.get("designation")));
                user.setRole(cleanString((String) rawUser.get("role")));

                List<Map<String, Object>> rawLocs = (List<Map<String, Object>>) rawUser.get("locations");
                List<UserLocation> locations = new ArrayList<>();
                if (rawLocs != null) {
                    for (Map<String, Object> rawLoc : rawLocs) {
                        UserLocation loc = new UserLocation();
                        loc.setCorp(cleanString((String) rawLoc.get("corp")));
                        loc.setZoneName(cleanString((String) rawLoc.get("zoneName")));
                        loc.setDivision(cleanString((String) rawLoc.get("division")));
                        loc.setCircleName(cleanString((String) rawLoc.get("circleName")));
                        loc.setWardName(cleanString((String) rawLoc.get("wardName")));
                        loc.setRole(cleanString((String) rawLoc.get("role")));
                        loc.setPhoneNumber(user.getPhoneNumber());
                        locations.add(loc);
                    }
                }
                user.setLocations(locations);
                userRepository.save(user);
                addedCount++;
            }

            // Ensure default admin user exists
            if (!userRepository.existsById("admin")) {
                User admin = new User();
                admin.setPhoneNumber("admin");
                admin.setName("System Administrator");
                admin.setPassword("admin");
                admin.setDesignation("Administrator");
                admin.setRole("ADMIN");
                userRepository.save(admin);
                addedCount++;
            }

            log.info("DatabaseSeeder finished checking users. Added {} missing users. Total users now: {}",
                    addedCount, userRepository.count());
        } catch (Exception e) {
            log.error("Error seeding users from json file: {}", e.getMessage(), e);
        }

        // 3. Ensure initial Revision 1 snapshots and baseline remarks for existing estimates
        try {
            List<Estimate> allEstimates = estimateRepository.findAll();
            int revCount = 0;
            for (Estimate est : allEstimates) {
                if (estimateRevisionRepository.countByEstimateId(est.getId()) == 0) {
                    EstimateRevision rev = new EstimateRevision();
                    rev.setEstimateId(est.getId());
                    rev.setRevisionNumber(1);
                    rev.setRevisionType("INITIAL_DRAFT");
                    rev.setStatusAtRevision(est.getStatus() != null ? est.getStatus() : "DRAFT");
                    rev.setNameOfWork(est.getNameOfWork());
                    rev.setGstPercent(est.getGstPercent());
                    rev.setUnforeseenAmount(est.getUnforeseenAmount());
                    rev.setGrandTotal(est.getGrandTotal());
                    rev.setCorp(est.getCorp());
                    rev.setZoneName(est.getZoneName());
                    rev.setDivision(est.getDivision());
                    rev.setCircleName(est.getCircleName());
                    rev.setWardName(est.getWardName());
                    rev.setOfficerPhone(est.getOfficerPhone());
                    rev.setOfficerName(est.getPreparedByName() != null ? est.getPreparedByName() : "Officer");
                    rev.setOfficerRole("MANAGER");
                    rev.setOfficerDesignation(est.getPreparedByDesignation() != null ? est.getPreparedByDesignation() : "Manager (AE)");
                    rev.setRemarks(est.getLastRemarks() != null ? est.getLastRemarks() : "Initial baseline estimate");
                    rev.setChangeSummary("Initial baseline snapshot");
                    rev.setSnapshotJson(mapper.writeValueAsString(est));
                    rev.setCreatedAt(est.getCreatedAt() != null ? est.getCreatedAt() : LocalDateTime.now());
                    estimateRevisionRepository.save(rev);

                    EstimateRemark rem = new EstimateRemark();
                    rem.setEstimateId(est.getId());
                    rem.setRevisionNumber(1);
                    rem.setOfficerPhone(est.getOfficerPhone() != null ? est.getOfficerPhone() : "SYSTEM");
                    rem.setOfficerName(est.getPreparedByName() != null ? est.getPreparedByName() : "System");
                    rem.setOfficerRole("MANAGER");
                    rem.setOfficerDesignation(est.getPreparedByDesignation() != null ? est.getPreparedByDesignation() : "Manager (AE)");
                    rem.setAction("CREATE");
                    rem.setFromStatus(null);
                    rem.setToStatus(est.getStatus() != null ? est.getStatus() : "DRAFT");
                    rem.setRemarks(est.getLastRemarks() != null ? est.getLastRemarks() : "Initial draft created");
                    rem.setTags("Draft");
                    rem.setCreatedAt(est.getCreatedAt() != null ? est.getCreatedAt() : LocalDateTime.now());
                    estimateRemarkRepository.save(rem);

                    revCount++;
                }
            }
            if (revCount > 0) {
                log.info("Seeded initial baseline Revision 1 for {} existing estimates", revCount);
            }
        } catch (Exception e) {
            log.info("Estimate baseline revision check skipped: {}", e.getMessage());
        }
    }

    private String cleanString(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replace("\u00E2\u20AC\u201C", "-")
                              .replace("\u00E2\u20AC\u201D", "-")
                              .replace("\u00e2\u0080\u0093", "-")
                              .replace("\u00e2\u0080\u0094", "-")
                              .replace("\u2013", "-")
                              .replace("\u2014", "-")
                              .replace("\u00A0", " ")
                              .replace("?", "-")
                              .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
