package com.hmwssb.works.seeder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DatabaseSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
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
            System.out.println("Users table already has " + userRepository.count() + " records with roles populated. Skipping seed.");
            return;
        }

        System.out.println("Clearing old users and seeding HMWSSB officer accounts from sample users.xlsx data...");
        userRepository.deleteAll();

        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("users_parsed.json");
        if (!resource.exists()) {
            System.err.println("Could not find users_parsed.json in classpath resources!");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Map<String, Object>> rawUsers = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            List<User> usersToSave = new ArrayList<>();

            for (Map<String, Object> rawUser : rawUsers) {
                User user = new User();
                user.setPhoneNumber(cleanString((String) rawUser.get("phoneNumber")));
                user.setName(cleanString((String) rawUser.get("name")));
                user.setPassword("1234"); // Default password for all officers
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
            System.out.println("Successfully seeded " + usersToSave.size() + " officer accounts from sample users.xlsx!");
        } catch (Exception e) {
            System.err.println("Error seeding users from json file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String cleanString(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replace("â€“", "-")
                              .replace("â\u0080\u0093", "-")
                              .replace("?", "-")
                              .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
