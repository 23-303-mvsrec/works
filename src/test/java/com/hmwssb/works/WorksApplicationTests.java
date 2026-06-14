package com.hmwssb.works;

import com.hmwssb.works.model.Item;
import com.hmwssb.works.repository.ItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorksApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ItemRepository itemRepository;

	private Item testItem1;
	private Item testItem2;

	@BeforeEach
	void setUp() {
		// Clean up any stray test entries if they exist
		itemRepository.deleteAllById(List.of(999991, 999992));

		testItem1 = new Item();
		testItem1.setSlno(999991);
		testItem1.setItemDescription("Lowering C.I. Pipes in trench");
		testItem1.setUnit("Meter");
		testItem1.setRate(150.0);

		testItem2 = new Item();
		testItem2.setSlno(999992);
		testItem2.setItemDescription("Lowering D.I. Pipes in ground");
		testItem2.setUnit("Meter");
		testItem2.setRate(200.0);

		itemRepository.saveAll(List.of(testItem1, testItem2));
	}

	@AfterEach
	void tearDown() {
		itemRepository.deleteAllById(List.of(999991, 999992));
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testMultiWordSearch() throws Exception {
		// 1. Search with sequential match
		mockMvc.perform(get("/api/items/search").param("q", "C.I. Pipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].itemDescription", hasItem("Lowering C.I. Pipes in trench")))
				.andExpect(jsonPath("$[*].itemDescription", not(hasItem("Lowering D.I. Pipes in ground"))));

		// 2. Search with non-sequential match (words in different order)
		mockMvc.perform(get("/api/items/search").param("q", "Pipes C.I."))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].itemDescription", hasItem("Lowering C.I. Pipes in trench")))
				.andExpect(jsonPath("$[*].itemDescription", not(hasItem("Lowering D.I. Pipes in ground"))));

		// 3. Search with multiple common words (should return both matches)
		mockMvc.perform(get("/api/items/search").param("q", "Lowering Pipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].itemDescription", hasItem("Lowering C.I. Pipes in trench")))
				.andExpect(jsonPath("$[*].itemDescription", hasItem("Lowering D.I. Pipes in ground")));

		// 4. Search with non-existent keyword combination (should not return either of
		// our test items)
		mockMvc.perform(get("/api/items/search").param("q", "Lowering C.I. ground"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].itemDescription", not(hasItem("Lowering C.I. Pipes in trench"))))
				.andExpect(jsonPath("$[*].itemDescription", not(hasItem("Lowering D.I. Pipes in ground"))));
	}

	@Autowired
	private com.hmwssb.works.repository.UserRepository userRepository;

	@Test
	void testUserRegistrationAndLogin() throws Exception {
		// Clean up test user if exists
		userRepository.deleteById("9990000000");

		// 1. Register User
		String userJson = """
				{
				  "phoneNumber": "9990000000",
				  "name": "Test Officer",
				  "password": "mypassword",
				  "designation": "Test Engineer",
				  "locations": [
				    {
				      "corp": "MMC",
				      "zoneName": "Malkajgiri",
				      "division": "1",
				      "circleName": "1 - Keesara",
				      "wardName": "1 - Keesara"
				    }
				  ]
				}
				""";

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users/register")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(userJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phoneNumber").value("9990000000"))
				.andExpect(jsonPath("$.name").value("Test Officer"))
				.andExpect(jsonPath("$.locations[0].wardName").value("1 - Keesara"));

		// 2. Login User with Correct Credentials
		String loginJson = """
				{
				  "phoneNumber": "9990000000",
				  "password": "mypassword"
				}
				""";

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users/login")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(loginJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phoneNumber").value("9990000000"))
				.andExpect(jsonPath("$.name").value("Test Officer"));

		// 3. Login User with Incorrect Credentials
		String invalidLoginJson = """
				{
				  "phoneNumber": "9990000000",
				  "password": "wrongpassword"
				}
				""";

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users/login")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(invalidLoginJson))
				.andExpect(status().isUnauthorized());

		// Clean up
		userRepository.deleteById("9990000000");
	}

	@Autowired
	private com.hmwssb.works.repository.EstimateRepository estimateRepository;

	@Test
	void testEstimateWorkflow() throws Exception {
		// Create a mock estimate matching seeded MANAGER's location
		com.hmwssb.works.model.Estimate est = new com.hmwssb.works.model.Estimate();
		est.setNameOfWork("Test Pipeline Work");
		est.setGstPercent(18.0);
		est.setGrandTotal(11800.0);
		est.setCorp("MMC");
		est.setZoneName("LB Nagar");
		est.setDivision("5");
		est.setCircleName("11 - Nagole");
		est.setWardName("51 - Kuntloor");
		est.setOfficerPhone("7995010510"); // MANAGER (K.RAMAKRISHNA GOUD)
		est.setStatus("DRAFT");

		final com.hmwssb.works.model.Estimate saved = estimateRepository.save(est);
		Integer estId = saved.getId();

		try {
			// 1. Manager (7995010510) forwards DRAFT -> SUBMITTED_TO_DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "7995010510"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"))
					.andExpect(jsonPath("$.preparedByName").value("K.RAMAKRISHNA GOUD"));

			// 2. MANAGER tries to edit / save again (should be blocked)
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/estimates")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content(String.format("""
							{
							  "id": %d,
							  "nameOfWork": "Modified by Manager",
							  "officerPhone": "7995010510"
							}
							""", estId)))
					.andExpect(status().isForbidden());

			// 3. DGM (7331185790) forwards SUBMITTED_TO_DGM -> SUBMITTED_TO_GM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "7331185790"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_GM"))
					.andExpect(jsonPath("$.verifiedByName").value("T.N SAINATH GOUD"));

			// 4. DGM (7331185790) tries to forward again (should be blocked as it's now
			// with GM)
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "7331185790"
							}
							"""))
					.andExpect(status().isBadRequest());

			// 5. GM (9989994708) returns SUBMITTED_TO_GM -> SUBMITTED_TO_DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "RETURN",
							  "officerPhone": "9989994708"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"))
					.andExpect(jsonPath("$.recommendedByName").value((String) null)); // signature cleared

			// 6. DGM forwards SUBMITTED_TO_DGM -> SUBMITTED_TO_GM again
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "7331185790"
							}
							"""))
					.andExpect(status().isOk());

			// 7. GM (9989994708) forwards SUBMITTED_TO_GM -> SUBMITTED_TO_CGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989994708"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_CGM"))
					.andExpect(jsonPath("$.recommendedByName").value("M.MAHENDER"));

			// 8. CGM (9989989507) forwards SUBMITTED_TO_CGM -> SUBMITTED_TO_DOP
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989989507"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DOP"))
					.andExpect(jsonPath("$.forwardedByName").value("P.NAGENDRA KUMAR"));

			// 9. DOP (9989999753) approves SUBMITTED_TO_DOP -> APPROVED
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989999753"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("APPROVED"))
					.andExpect(jsonPath("$.sanctionedByName").value("Vasa SatyaNarayana"));

		} finally {
			estimateRepository.deleteById(estId);
		}
	}

	@Test
	void testRoleSpecificLocationQueryValidation() throws Exception {
		// 1. Create a mock estimate for Ward 51 under Circle 11
		com.hmwssb.works.model.Estimate est = new com.hmwssb.works.model.Estimate();
		est.setNameOfWork("Scoped Estimate Test");
		est.setGstPercent(18.0);
		est.setGrandTotal(5000.0);
		est.setCorp("MMC");
		est.setZoneName("LB Nagar");
		est.setDivision("5");
		est.setCircleName("11 - Nagole");
		est.setWardName("51 - Kuntloor");
		est.setOfficerPhone("7995010510"); // MANAGER (K.RAMAKRISHNA GOUD)
		est.setStatus("DRAFT");

		final com.hmwssb.works.model.Estimate saved = estimateRepository.save(est);
		Integer estId = saved.getId();

		try {
			// A. Test listing for Manager K.RAMAKRISHNA GOUD (7995010510) in his correct Ward scope -> OK
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "7995010510")
					.param("role", "MANAGER")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "5")
					.param("circleName", "11 - Nagole")
					.param("wardName", "51 - Kuntloor"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", hasItem(estId)));

			// B. Test listing for Manager K.RAMAKRISHNA GOUD in an incorrect Ward scope -> FORBIDDEN
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "7995010510")
					.param("role", "MANAGER")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "5")
					.param("circleName", "11 - Nagole")
					.param("wardName", "52 - Another Ward"))
					.andExpect(status().isForbidden());

			// C. Test listing for DGM T.N SAINATH GOUD (7331185790) in his correct Circle scope -> OK
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "7331185790")
					.param("role", "DGM")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "5")
					.param("circleName", "11 - Nagole"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", hasItem(estId)));

			// D. Test listing for DGM T.N SAINATH GOUD in incorrect Circle scope -> FORBIDDEN
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "7331185790")
					.param("role", "DGM")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "5")
					.param("circleName", "12 - Saroornagar"))
					.andExpect(status().isForbidden());

			// E. Test listing for DGM T.N SAINATH GOUD with MANAGER role (which he doesn't have) -> FORBIDDEN
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "7331185790")
					.param("role", "MANAGER")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "5")
					.param("circleName", "11 - Nagole")
					.param("wardName", "51 - Kuntloor"))
					.andExpect(status().isForbidden());

		} finally {
			estimateRepository.deleteById(estId);
		}
	}
}
