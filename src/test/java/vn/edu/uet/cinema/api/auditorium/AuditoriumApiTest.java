package vn.edu.uet.cinema.api.auditorium;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.uet.cinema.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditoriumApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void adminCreatesAndPubliclyReadsStableAuditoriumLayout() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		String cinema = cinemaId(admin, "Auditorium cinema " + UUID.randomUUID());
		String otherCinema = cinemaId(admin, "Other cinema " + UUID.randomUUID());

		String created = mvc.perform(post("/api/cinemas/" + cinema + "/auditoriums")
				.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content(auditoriumJson("Main hall", 3, 2, "[\"B\"]", "[\"C\"]")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.cinemaId").value(cinema))
			.andExpect(jsonPath("$.name").value("Main hall")).andExpect(jsonPath("$.rows").value(3))
			.andExpect(jsonPath("$.seatsPerRow").value(2)).andExpect(jsonPath("$.seats").isArray())
			.andExpect(jsonPath("$.seats[0].label").value("A1"))
			.andExpect(jsonPath("$.seats[2].type").value("VIP"))
			.andExpect(jsonPath("$.seats[4].type").value("COUPLE"))
			.andReturn().getResponse().getContentAsString();
		String auditorium = JsonPath.read(created, "$.id");
		String firstSeatId = JsonPath.read(created, "$.seats[0].id");

		mvc.perform(get("/api/cinemas/" + cinema + "/auditoriums"))
			.andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(hasItem(auditorium)))
			.andExpect(jsonPath("$[?(@.id=='" + auditorium + "')].seats").doesNotExist());
		mvc.perform(get("/api/auditoriums/" + auditorium)).andExpect(status().isOk())
			.andExpect(jsonPath("$.seats[0].id").value(firstSeatId));
		mvc.perform(get("/api/auditoriums/" + auditorium)).andExpect(status().isOk())
			.andExpect(jsonPath("$.seats[0].id").value(firstSeatId));

		mvc.perform(post("/api/cinemas/" + cinema + "/auditoriums").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(auditoriumJson("Main hall", 1, 1, "[]", "[]")))
			.andExpect(status().isConflict()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(post("/api/cinemas/" + otherCinema + "/auditoriums").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(auditoriumJson("Main hall", 1, 1, "[]", "[]")))
			.andExpect(status().isCreated());

		mvc.perform(delete("/api/cinemas/" + cinema).header("Authorization", "Bearer " + admin))
			.andExpect(status().isConflict());
		mvc.perform(delete("/api/auditoriums/" + auditorium).header("Authorization", "Bearer " + admin))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/cinemas/" + cinema).header("Authorization", "Bearer " + admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void validatesIdsLayoutAndWriteAuthorization() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		String missing = UUID.randomUUID().toString();
		mvc.perform(post("/api/cinemas/" + missing + "/auditoriums").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(auditoriumJson("Missing", 1, 1, "[]", "[]")))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/cinemas/" + missing + "/auditoriums")).andExpect(status().isNotFound());
		mvc.perform(get("/api/auditoriums/" + missing)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/auditoriums/" + missing).header("Authorization", "Bearer " + admin))
			.andExpect(status().isNotFound());

		String cinema = cinemaId(admin, "Validation cinema " + UUID.randomUUID());
		for (String body : new String[] {
			"{\"name\":\"Bad\",\"rows\":0,\"seatsPerRow\":1,\"vipRows\":[],\"coupleRows\":[]}",
			"{\"name\":\"Bad\",\"rows\":1,\"seatsPerRow\":1,\"vipRows\":[\"B\"],\"coupleRows\":[]}",
			"{\"name\":\"Bad\",\"rows\":2,\"seatsPerRow\":1,\"vipRows\":[\"A\"],\"coupleRows\":[\"A\"]}"
		}) {
			mvc.perform(post("/api/cinemas/" + cinema + "/auditoriums").header("Authorization", "Bearer " + admin)
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mvc.perform(post("/api/cinemas/" + cinema + "/auditoriums").contentType(MediaType.APPLICATION_JSON)
				.content(auditoriumJson("No auth", 1, 1, "[]", "[]"))).andExpect(status().isUnauthorized());
		String customer = token(registerEmail(), "password123");
		mvc.perform(delete("/api/auditoriums/" + UUID.randomUUID()).header("Authorization", "Bearer " + customer))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsAuditoriumsExceedingSeatLimit() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		String cinema = cinemaId(admin, "Seat limit cinema " + UUID.randomUUID());

		mvc.perform(post("/api/cinemas/" + cinema + "/auditoriums")
				.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content(auditoriumJson("Too many seats", 2, Integer.MAX_VALUE, "[]", "[]")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void catalogueOpenApiMarksOnlyWritesAsProtected() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/cinemas/{id}/auditoriums'].get.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/cinemas/{id}/auditoriums'].post.security[0].bearerAuth").exists())
			.andExpect(jsonPath("$.paths['/api/auditoriums/{id}'].get.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/auditoriums/{id}'].delete.security[0].bearerAuth").exists());
	}

	private String cinemaId(String admin, String name) throws Exception {
		String body = mvc.perform(post("/api/cinemas").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"address\":\"Address\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String token(String email, String password) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private String registerEmail() throws Exception {
		String email = "customer-" + UUID.randomUUID() + "@example.com";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"fullName\":\"Customer\"}"))
			.andExpect(status().isCreated());
		return email;
	}

	private static String auditoriumJson(String name, int rows, int seatsPerRow, String vipRows,
		String coupleRows) {
		return "{\"name\":\"%s\",\"rows\":%d,\"seatsPerRow\":%d,\"vipRows\":%s,\"coupleRows\":%s}"
			.formatted(name, rows, seatsPerRow, vipRows, coupleRows);
	}
}
