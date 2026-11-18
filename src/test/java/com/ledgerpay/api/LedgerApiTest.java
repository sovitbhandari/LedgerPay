package com.ledgerpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ledgerpay.support.ApiDriver;
import com.ledgerpay.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class LedgerApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiDriver api;

    @BeforeEach
    void setUp() {
        api = new ApiDriver(mockMvc);
    }

    @Test
    void transferPersistsBalancedEntriesAndReplaysTheSameKey() throws Exception {
        String email = "ada-" + UUID.randomUUID() + "@example.com";
        String token = api.register(email);
        String bob = api.register("bob-" + UUID.randomUUID() + "@example.com");
        UUID spending = api.createAccount(token, "Spending", "USD");
        UUID savings = api.createAccount(bob, "Savings", "USD");
        api.deposit(token, spending, "25.00", "deposit-" + UUID.randomUUID());

        String key = "transfer-" + UUID.randomUUID();
        MvcResult first = api.transfer(token, key, spending, savings, "USD", "10.50");
        MvcResult replay = api.transfer(token, key, spending, savings, "USD", "10.5");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        String transferId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        assertThat((String) JsonPath.read(replay.getResponse().getContentAsString(), "$.id")).isEqualTo(transferId);
        assertThat((Integer) JsonPath.read(first.getResponse().getContentAsString(), "$.amountMinor")).isEqualTo(1050);

        MvcResult conflict = api.transfer(token, key, spending, savings, "USD", "1.00");
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(conflict.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        assertThat(balanceMinor(token, spending)).isEqualTo(1450);
        assertThat(balanceMinor(bob, savings)).isEqualTo(1050);
        assertThat(jdbc.queryForObject(
                        "select count(*) from ledger_entries where transfer_id = cast(? as uuid)",
                        Integer.class,
                        transferId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        """
                        select coalesce(sum(amount_minor) filter (where direction = 'DEBIT'), 0)
                             - coalesce(sum(amount_minor) filter (where direction = 'CREDIT'), 0)
                          from ledger_entries
                         where transfer_id = cast(? as uuid)
                        """,
                        Long.class,
                        transferId))
                .isZero();
        assertThat(jdbc.queryForObject("select coalesce(sum(balance_minor), 0) from accounts", Long.class)).isZero();

        String hash = jdbc.queryForObject("select password_hash from app_users where email = ?", String.class, email);
        assertThat(hash).startsWith("$2a$12$").doesNotContain("Password12345");

        MvcResult ledger = api.get("/api/v1/accounts/" + spending + "/ledger?page=0&size=10", token);
        assertThat(ledger.getResponse().getStatus()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(ledger.getResponse().getContentAsString(), "$.totalElements")).isEqualTo(2);
    }

    @Test
    void rejectsInsufficientFundsWithoutPosting() throws Exception {
        String token = api.register("limit-" + UUID.randomUUID() + "@example.com");
        String bob = api.register("limit-bob-" + UUID.randomUUID() + "@example.com");
        UUID source = api.createAccount(token, "Source", "USD");
        UUID destination = api.createAccount(bob, "Destination", "USD");
        api.deposit(token, source, "1.00", "seed-" + UUID.randomUUID());
        long transfersBefore = jdbc.queryForObject("select count(*) from transfers", Long.class);

        MvcResult result = api.transfer(token, "too-much-" + UUID.randomUUID(), source, destination, "USD", "2.00");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(jdbc.queryForObject("select count(*) from transfers", Long.class)).isEqualTo(transfersBefore);
        assertThat(balanceMinor(token, source)).isEqualTo(100);
    }

    @Test
    void paginatesLedgerHistoryAndSupportsYen() throws Exception {
        String token = api.register("yen-" + UUID.randomUUID() + "@example.com");
        UUID account = api.createAccount(token, "Cash", "JPY");
        api.deposit(token, account, "100", "yen-a-" + UUID.randomUUID());
        api.deposit(token, account, "40", "yen-b-" + UUID.randomUUID());
        api.deposit(token, account, "5", "yen-c-" + UUID.randomUUID());

        MvcResult page = api.get("/api/v1/accounts/" + account + "/ledger?page=0&size=2", token);
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(page.getResponse().getContentAsString(), "$.totalElements")).isEqualTo(3);
        assertThat(JsonPath.<java.util.List<?>>read(page.getResponse().getContentAsString(), "$.entries")).hasSize(2);
        assertThat(balanceMinor(token, account)).isEqualTo(145);

        MvcResult rejected = api.transfer(
                token, "yen-bad-" + UUID.randomUUID(), account, account, "JPY", "1.5");
        assertThat(rejected.getResponse().getStatus()).isIn(400, 422);
    }

    @Test
    void frozenAccountCannotSend() throws Exception {
        String token = api.register("frozen-" + UUID.randomUUID() + "@example.com");
        String bob = api.register("frozen-bob-" + UUID.randomUUID() + "@example.com");
        UUID source = api.createAccount(token, "Source", "EUR");
        UUID destination = api.createAccount(bob, "Destination", "EUR");
        api.deposit(token, source, "5.00", "eur-" + UUID.randomUUID());
        mockStatus(token, source, "FROZEN");

        MvcResult result = api.transfer(token, "frozen-pay-" + UUID.randomUUID(), source, destination, "EUR", "1.00");
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(balanceMinor(token, source)).isEqualTo(500);
    }

    private void mockStatus(String token, UUID accountId, String status) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private int balanceMinor(String token, UUID accountId) throws Exception {
        MvcResult result = api.get("/api/v1/accounts/" + accountId + "/balance", token);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.balanceMinor");
    }
}
