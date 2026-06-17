package com.hmwssb.works.seeder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.model.Jurisdiction;
import com.hmwssb.works.repository.UserRepository;
import com.hmwssb.works.repository.JurisdictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final UserRepository userRepository;
    private final JurisdictionRepository jurisdictionRepository;

    public DatabaseSeeder(UserRepository userRepository, JurisdictionRepository jurisdictionRepository) {
        this.userRepository = userRepository;
        this.jurisdictionRepository = jurisdictionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

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
        boolean needsReseed = false;
        if (userRepository.count() > 0) {
            long locationsWithNullRole = userRepository.findAll().stream()
                .flatMap(u -> u.getLocations().stream())
                .filter(loc -> loc.getRole() == null)
                .count();
            if (locationsWithNullRole > 0) {
                needsReseed = true;
            }
        } else {
            needsReseed = true;
        }

        if (!needsReseed) {
            log.info("Users table already has {} records with roles populated. Skipping seed.", userRepository.count());
            return;
        }

        log.info("Clearing old users and seeding HMWSSB officer accounts from sample users.xlsx data...");
        userRepository.deleteAll();

        ClassPathResource resource = new ClassPathResource("users_parsed.json");
        if (!resource.exists()) {
            log.error("Could not find users_parsed.json in classpath resources!");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Map<String, Object>> rawUsers = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            List<User> usersToSave = new ArrayList<>();

            for (Map<String, Object> rawUser : rawUsers) {
                User user = new User();
                user.setPhoneNumber(cleanString((String) rawUser.get("phoneNumber")));
                user.setName(cleanString((String) rawUser.get("name")));
                user.setPassword("1234");
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
                usersToSave.add(user);
            }

            userRepository.saveAll(usersToSave);
            log.info("Successfully seeded {} officer accounts from sample users.xlsx", usersToSave.size());
        } catch (Exception e) {
            log.error("Error seeding users from json file: {}", e.getMessage(), e);
        }
    }

    private String cleanString(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replace("\u00e2\u0080\u0093", "-")
                              .replace("\u00e2\u0080\u0093", "-")
                              .replace("?", "-")
                              .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
