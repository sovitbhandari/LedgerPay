package com.ledgerpay.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public final class ApiDriver {

    private final MockMvc mockMvc;

    public ApiDriver(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password12345"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    public UUID createAccount(String token, String name, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","currency":"%s"}
                                """.formatted(name, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    public void deposit(String token, UUID accountId, String amount, String key) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s"}
                                """.formatted(amount)))
                .andExpect(status().isCreated());
    }

    public MvcResult transfer(String token, String key, UUID source, UUID destination, String currency, String amount)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountId":"%s","destinationAccountId":"%s","currency":"%s","amount":"%s"}
                                """.formatted(source, destination, currency, amount)))
                .andReturn();
    }

    public MvcResult get(String path, String token) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }
}
