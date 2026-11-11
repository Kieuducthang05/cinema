package vn.edu.uet.cinema.api.cinema;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.cinema.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CinemaApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void adminCanCreateReadUpdateAndDeleteCinema() throws Exception {
		String admin = token();
		String created = mvc.perform(post("/api/cinemas").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(cinemaJson("Cinema Alpha", "1 Alpha Street")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Cinema Alpha"))
			.andExpect(jsonPath("$.address").value("1 Alpha Street"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");

		mvc.perform(get("/api/cinemas"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id").value(hasItem(id)));
		mvc.perform(get("/api/cinemas/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Cinema Alpha"))
			.andExpect(jsonPath("$.address").value("1 Alpha Street"));

		mvc.perform(put("/api/cinemas/" + id).header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(cinemaJson("Cinema Beta", "2 Beta Street")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Cinema Beta"))
			.andExpect(jsonPath("$.address").value("2 Beta Street"));

		mvc.perform(delete("/api/cinemas/" + id).header("Authorization", "Bearer " + admin))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/cinemas/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void cinemaWithAuditoriumCannotBeDeleted() throws Exception {
		String admin = token();
		String cinemaBody = mvc.perform(post("/api/cinemas").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(cinemaJson("Cinema with room " + UUID.randomUUID(), "Address")))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String cinemaId = JsonPath.read(cinemaBody, "$.id");
		mvc.perform(post("/api/cinemas/" + cinemaId + "/auditoriums").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Main\",\"rows\":1,\"seatsPerRow\":1,\"vipRows\":[],\"coupleRows\":[]}"))
			.andExpect(status().isCreated());

		mvc.perform(delete("/api/cinemas/" + cinemaId).header("Authorization", "Bearer " + admin))
			.andExpect(status().isConflict());
	}

	@Test
	void unknownCinemaWritesAreNotFound() throws Exception {
		String admin = token();
		mvc.perform(put("/api/cinemas/" + UUID.randomUUID()).header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(cinemaJson("Missing", "Nowhere")))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/cinemas/" + UUID.randomUUID()).header("Authorization", "Bearer " + admin))
			.andExpect(status().isNotFound());
	}

	private String token() throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cinema.local\",\"password\":\"admin12345\"}"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String cinemaJson(String name, String address) {
		return "{\"name\":\"%s\",\"address\":\"%s\"}".formatted(name, address);
	}
}
