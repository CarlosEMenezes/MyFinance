package ie.budgetTracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import ie.budgetTracker.api.auth.SessionCookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;

/**
 * ADR-11's central claim, end to end: one user cannot see another's anything.
 *
 * This is the test that fails loudly when the schema and the repository filters
 * are got wrong, and it is the reason authentication was built before the seven
 * remaining feature slices rather than after them. Every slice that adds a
 * user-owned table gets a test shaped like this one.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserIsolationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private MockMvc mvc;

	private MockMvc mvc() {
		if (mvc == null) {
			mvc = MockMvcBuilders.webAppContextSetup(context)
					.addFilters(springSecurityFilterChain)
					.build();
		}
		return mvc;
	}

	/** Registers someone and returns the session cookie they were given. */
	private Cookie register(String email) throws Exception {
		MvcResult result = mvc().perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"a-long-enough-passphrase","name":"Someone"}"""
						.formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		Cookie session = result.getResponse().getCookie(SessionCookie.NAME);
		assertThat(session).as("a session cookie is set on registration").isNotNull();
		return session;
	}

	private String createAccount(Cookie session, String name) throws Exception {
		return mvc().perform(post("/api/v1/accounts")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"%s","kind":"CASH","balance":12000,"currency":"EUR",
						 "includeInTotals":true}""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("a user sees only their own accounts")
	void aUserSeesOnlyTheirOwnAccounts() throws Exception {
		Cookie ada = register("ada@example.com");
		Cookie grace = register("grace@example.com");

		createAccount(ada, "Ada's wallet");
		createAccount(grace, "Grace's wallet");

		mvc().perform(get("/api/v1/accounts").cookie(ada))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Ada's wallet"));

		mvc().perform(get("/api/v1/accounts").cookie(grace))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Grace's wallet"));
	}

	@Test
	@DisplayName("another user's account id answers 404, never 403")
	void anotherUsersAccountIsNotFoundRatherThanForbidden() throws Exception {
		Cookie ada = register("ada2@example.com");
		Cookie grace = register("grace2@example.com");

		String adasAccount = createAccount(ada, "Ada's wallet");
		String id = adasAccount.replaceAll("^\\{\"id\":\"([^\"]+)\".*$", "$1");

		// A 403 would confirm the id exists, which tells someone enumerating ids
		// exactly which ones are real. As far as this API is concerned, another
		// person's data does not exist.
		mvc().perform(post("/api/v1/accounts/" + id + "/pockets")
				.cookie(grace)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Sneaky","balance":100}"""))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("each user reads their own profile")
	void eachUserReadsTheirOwnProfile() throws Exception {
		Cookie ada = register("ada3@example.com");
		Cookie grace = register("grace3@example.com");

		mvc().perform(patch(ada, """
				{"country":"England"}"""))
				.andExpect(status().isOk());

		mvc().perform(get("/api/v1/users/me").cookie(grace))
				.andExpect(jsonPath("$.country").doesNotExist());
	}

	private org.springframework.test.web.servlet.RequestBuilder patch(Cookie session, String body) {
		return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.patch("/api/v1/users/me")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	@Test
	@DisplayName("without a session, nothing is readable")
	void withoutASessionNothingIsReadable() throws Exception {
		// The default is deny: a new endpoint is protected the moment it exists.
		mvc().perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
		mvc().perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a signed-out session stops working immediately")
	void aSignedOutSessionStopsWorkingImmediately() throws Exception {
		Cookie ada = register("ada4@example.com");

		mvc().perform(get("/api/v1/accounts").cookie(ada)).andExpect(status().isOk());

		mvc().perform(post("/api/v1/auth/logout").cookie(ada))
				.andExpect(status().isNoContent());

		// This is what an opaque, stored token buys over a signed one: the session
		// is gone rather than merely unwelcome (ADR-11).
		mvc().perform(get("/api/v1/accounts").cookie(ada)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a made-up token is not a session")
	void aMadeUpTokenIsNotASession() throws Exception {
		mvc().perform(get("/api/v1/accounts").cookie(new Cookie(SessionCookie.NAME, "not-a-token")))
				.andExpect(status().isUnauthorized());
	}
}
