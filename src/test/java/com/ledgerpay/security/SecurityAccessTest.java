package com.ledgerpay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ledgerpay.support.ApiDriver;
import com.ledgerpay.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class SecurityAccessTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ApiDriver api;

    @BeforeEach
    void setUp() {
        api = new ApiDriver(mockMvc);
    }

    @Test
    void healthIsPublicAndLedgerRoutesRequireAValidToken() throws Exception {
        MvcResult health = api.get("/actuator/health", null);
        assertThat(health.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(health.getResponse().getContentAsString(), "$.status")).isEqualTo("UP");

        MvcResult missing = api.get("/api/v1/accounts", null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) JsonPath.read(missing.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("UNAUTHENTICATED");

        MvcResult invalid = api.get("/api/v1/accounts", "not-a-token");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void usersCannotReadOrDebitAnotherUsersAccount() throws Exception {
        String alice = api.register("alice-sec-" + UUID.randomUUID() + "@example.com");
        String bob = api.register("bob-sec-" + UUID.randomUUID() + "@example.com");
        UUID aliceAccount = api.createAccount(alice, "Alice", "GBP");
        UUID bobAccount = api.createAccount(bob, "Bob", "GBP");
        api.deposit(bob, bobAccount, "8.00", "bob-fund-" + UUID.randomUUID());

        MvcResult balance = api.get("/api/v1/accounts/" + bobAccount + "/balance", alice);
        assertThat(balance.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(balance.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("OWNERSHIP_VIOLATION");

        MvcResult ledger = api.get("/api/v1/accounts/" + bobAccount + "/ledger", alice);
        assertThat(ledger.getResponse().getStatus()).isEqualTo(403);

        MvcResult stolen = api.transfer(alice, "steal-" + UUID.randomUUID(), bobAccount, aliceAccount, "GBP", "1.00");
        assertThat(stolen.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(stolen.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("OWNERSHIP_VIOLATION");

        MvcResult missingAccount = api.get("/api/v1/accounts/" + UUID.randomUUID() + "/balance", alice);
        assertThat(missingAccount.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void loginFailuresDoNotRevealWhichPartWasWrong() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        api.register(email);

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPassword1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknown = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing-%s","password":"WrongPassword1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat((String) JsonPath.read(wrongPassword.getResponse().getContentAsString(), "$.message"))
                .isEqualTo(JsonPath.read(unknown.getResponse().getContentAsString(), "$.message"));

        MvcResult loggedIn = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password12345"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat((String) JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.accessToken")).isNotBlank();
    }

    @Test
    void missingIdempotencyKeyAndShortPasswordAreRejected() throws Exception {
        MvcResult shortPassword = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"short-%s@example.com","password":"short1"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat((String) JsonPath.read(shortPassword.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("VALIDATION_ERROR");

        String token = api.register("idem-" + UUID.randomUUID() + "@example.com");
        UUID source = api.createAccount(token, "A", "USD");
        UUID destination = api.createAccount(token, "B", "USD");
        MvcResult missingKey = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountId":"%s","destinationAccountId":"%s","currency":"USD","amount":"1.00"}
                                """.formatted(source, destination)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat((String) JsonPath.read(missingKey.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }
}
