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

	@Test
	void testScenario_9154866717_To_9989994369_To_9989994708_FullLifecycle() throws Exception {
		// 1. Manager (9154866717 - SANGOJU SIRIVENNELA) creates an estimate in Ward 49, Circle 14, Div 6
		com.hmwssb.works.model.Estimate est = new com.hmwssb.works.model.Estimate();
		est.setNameOfWork("Pipeline Extension Sahebnagar");
		est.setGstPercent(18.0);
		est.setGrandTotal(25000.0);
		est.setCorp("MMC");
		est.setZoneName("LB Nagar");
		est.setDivision("6");
		est.setCircleName("14 - Hayathnagar");
		est.setWardName("49 - Sahebnagar");
		est.setOfficerPhone("9154866717");
		est.setStatus("DRAFT");

		final com.hmwssb.works.model.Estimate saved = estimateRepository.save(est);
		Integer estId = saved.getId();

		try {
			// Step 1: Manager 9154866717 forwards DRAFT -> SUBMITTED_TO_DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9154866717",
							  "remarks": "Draft estimate completed for Ward 49"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"))
					.andExpect(jsonPath("$.preparedByName").value("SANGOJU SIRIVENNELA"))
					.andExpect(jsonPath("$.officerPhone").value("9154866717"));

			// Step 2: DGM 9989994369 (K.NAGAR RAJU, Circle 14) forwards SUBMITTED_TO_DGM -> SUBMITTED_TO_GM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989994369",
							  "remarks": "Verified and forwarded to GM"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_GM"))
					.andExpect(jsonPath("$.verifiedByName").value("K.NAGAR RAJU"))
					.andExpect(jsonPath("$.officerPhone").value("9154866717"));

			// Step 3: GM 9989994708 (M.MAHENDER, Divisions 5 & 6) queries estimates:
			// A. Querying across all assigned divisions (no division parameter) -> MUST BE VISIBLE
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "9989994708")
					.param("role", "GM")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", hasItem(estId)));

			// B. Querying explicitly for Division 6 -> MUST BE VISIBLE
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "9989994708")
					.param("role", "GM")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "6"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", hasItem(estId)));

			// C. Querying explicitly for Division 5 -> MUST NOT BE IN LIST (filtered)
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "9989994708")
					.param("role", "GM")
					.param("corp", "MMC")
					.param("zoneName", "LB Nagar")
					.param("division", "5"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", not(hasItem(estId))));

			// Step 4: GM 9989994708 forwards SUBMITTED_TO_GM -> SUBMITTED_TO_CGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989994708",
							  "remarks": "Recommended by GM for administrative approval"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_CGM"))
					.andExpect(jsonPath("$.recommendedByName").value("M.MAHENDER"))
					.andExpect(jsonPath("$.officerPhone").value("9154866717"));

			// Step 5: CGM 9989989507 (P.NAGENDRA KUMAR) forwards SUBMITTED_TO_CGM -> SUBMITTED_TO_DOP
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989989507",
							  "remarks": "Reviewed and submitted for final sanction"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DOP"))
					.andExpect(jsonPath("$.forwardedByName").value("P.NAGENDRA KUMAR"))
					.andExpect(jsonPath("$.officerPhone").value("9154866717"));

			// Step 6: DOP 9989999753 (Vasa SatyaNarayana) approves SUBMITTED_TO_DOP -> APPROVED
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989999753",
							  "remarks": "Technically sanctioned and approved"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("APPROVED"))
					.andExpect(jsonPath("$.sanctionedByName").value("Vasa SatyaNarayana"))
					.andExpect(jsonPath("$.officerPhone").value("9154866717"));

			// Step 7: Verify all officers (Manager, DGM, GM, CGM, DOP) can see the APPROVED estimate
			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "9154866717")
					.param("role", "MANAGER"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", hasItem(estId)));

			mockMvc.perform(get("/api/estimates")
					.param("officerPhone", "9989994708")
					.param("role", "GM"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].id", hasItem(estId)));

		} finally {
			estimateRepository.deleteById(estId);
		}
	}

	@Test
	void testHierarchyEndpoint() throws Exception {
		mockMvc.perform(get("/api/jurisdictions/hierarchy"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.MMC").exists())
				.andExpect(jsonPath("$.MMC['LB Nagar']").exists());
	}

	@Test
	void testEnDashEncodingAndFullReturnLifecycle() throws Exception {
		// Create estimate with unicode en-dash in circleName and wardName
		String estJson = """
				{
				  "nameOfWork": "Pipeline Extension with EnDash",
				  "gstPercent": 18.0,
				  "unforeseenAmount": 500.0,
				  "corp": "MMC",
				  "zoneName": "LB Nagar",
				  "division": "6",
				  "circleName": "14 \\u2013 Hayathnagar",
				  "wardName": "49 \\u2013 Sahebnagar",
				  "officerPhone": "9154866717",
				  "items": [
				    {
				      "isMaterial": "Yes",
				      "description": "Lowering C.I. Pipes in trench",
				      "num": 10,
				      "length": 5.0,
				      "rate": 150.0,
				      "unit": "Meter"
				    }
				  ]
				}
				""";

		String responseBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/estimates")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(estJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andReturn().getResponse().getContentAsString();

		com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
		Integer estId = root.get("id").asInt();

		try {
			// Step 1: MANAGER forwards to DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9154866717",
							  "remarks": "Draft ready"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"))
					.andExpect(jsonPath("$.preparedByName").value("SANGOJU SIRIVENNELA"));

			// Step 2: DGM forwards to GM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989994369",
							  "remarks": "DGM verified"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_GM"))
					.andExpect(jsonPath("$.verifiedByName").value("K.NAGAR RAJU"));

			// Step 3: GM returns to DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "RETURN",
							  "officerPhone": "9989994708",
							  "remarks": "Need revision in rates"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"))
					.andExpect(jsonPath("$.verifiedByName").value("K.NAGAR RAJU")); // verified by remains from previous step or cleared per spec

			// Step 4: DGM returns to MANAGER
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "RETURN",
							  "officerPhone": "9989994369",
							  "remarks": "Returned for corrections"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("DRAFT"));

			// Step 5: MANAGER re-forwards to DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9154866717",
							  "remarks": "Corrected draft"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"));

			// Step 6: DGM re-forwards to GM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989994369",
							  "remarks": "Re-verified"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_GM"));

			// Step 7: GM forwards to CGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9989994708",
							  "remarks": "GM recommended"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_CGM"))
					.andExpect(jsonPath("$.recommendedByName").value("M.MAHENDER"));

		} finally {
			estimateRepository.deleteById(estId);
		}
	}

	@Test
	void testEstimateRevisionAndRemarksLifecycle() throws Exception {
		// 1. Create initial estimate
		String createJson = """
				{
				  "nameOfWork": "Version Control Pipeline Test",
				  "corp": "MMC",
				  "zoneName": "LB Nagar",
				  "division": "6",
				  "circleName": "14 - Hayathnagar",
				  "wardName": "49 - Sahebnagar",
				  "officerPhone": "9154866717",
				  "gstPercent": 18.0,
				  "unforeseenAmount": 0.0,
				  "items": [
				    {
				      "sno": 1,
				      "isMaterial": "Yes",
				      "description": "Lowering C.I. Pipes in trench",
				      "quantity": 10.0,
				      "rate": 150.0,
				      "unit": "Meter",
				      "amount": 1500.0
				    }
				  ]
				}
				""";

		String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/estimates")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(createJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andReturn().getResponse().getContentAsString();

		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
		int estId = root.get("id").asInt();

		try {
			// 2. Verify initial revision snapshot and remark
			mockMvc.perform(get("/api/estimates/" + estId + "/revisions"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].revisionNumber").value(1))
					.andExpect(jsonPath("$[0].revisionType").value("INITIAL_DRAFT"))
					.andExpect(jsonPath("$[0].officerPhone").value("9154866717"));

			mockMvc.perform(get("/api/estimates/" + estId + "/remarks"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].action").value("CREATE"))
					.andExpect(jsonPath("$[0].toStatus").value("DRAFT"));

			// 3. Manager forwards to DGM
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "FORWARD",
							  "officerPhone": "9154866717",
							  "remarks": "Draft estimate ready for scrutiny",
							  "tags": "Draft"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SUBMITTED_TO_DGM"));

			// 4. DGM returns with scrutiny remarks
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/action")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "action": "RETURN",
							  "officerPhone": "9989994369",
							  "remarks": "Please increase rate to 200 and add valve fittings",
							  "tags": "Rate,Scope"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("DRAFT"));

			// 5. Manager updates measurements (adds item 2 and modifies rate of item 1)
			String updateJson = """
					{
					  "id": %d,
					  "nameOfWork": "Version Control Pipeline Test",
					  "corp": "MMC",
					  "zoneName": "LB Nagar",
					  "division": "6",
					  "circleName": "14 - Hayathnagar",
					  "wardName": "49 - Sahebnagar",
					  "officerPhone": "9154866717",
					  "gstPercent": 18.0,
					  "unforeseenAmount": 0.0,
					  "lastRemarks": "Updated rate and added valve fittings as per DGM observation",
					  "items": [
					    {
					      "sno": 1,
					      "isMaterial": "Yes",
					      "description": "Lowering C.I. Pipes in trench",
					      "quantity": 10.0,
					      "rate": 200.0,
					      "unit": "Meter",
					      "amount": 2000.0
					    },
					    {
					      "sno": 2,
					      "isMaterial": "Yes",
					      "description": "Supply of 100mm Sluice Valve",
					      "quantity": 1.0,
					      "rate": 3500.0,
					      "unit": "Nos",
					      "amount": 3500.0
					    }
					  ]
					}
					""".formatted(estId);

			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content(updateJson))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.grandTotal").value(6490.0)); // (2000 + 3500) * 1.18 = 6490.0

			// 6. Test Diff Endpoint between Rev 1 and Rev 4 (Initial vs Measurement Update)
			mockMvc.perform(get("/api/estimates/" + estId + "/revisions/diff")
					.param("v1", "1")
					.param("v2", "4"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.financialDelta.difference").value(4720.0)) // 6490 - 1770 = 4720.0
					.andExpect(jsonPath("$.itemDiffs").isArray());

			// 7. Add custom Scrutiny observation note
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/remarks")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.content("""
							{
							  "officerPhone": "9989994369",
							  "remarks": "Reviewed revision measurements. Looks good.",
							  "tags": "Verified"
							}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.action").value("SCRUTINY_NOTE"))
					.andExpect(jsonPath("$.remarks").value("Reviewed revision measurements. Looks good."));

			// 8. Test Restore Revision back to Rev 1
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/estimates/" + estId + "/revisions/1/restore")
					.param("officerPhone", "9154866717"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.grandTotal").value(1770.0)) // (1500 * 1.18 = 1770.0)
					.andExpect(jsonPath("$.items.length()").value(1));

		} finally {
			estimateRepository.deleteById(estId);
		}
	}
}
