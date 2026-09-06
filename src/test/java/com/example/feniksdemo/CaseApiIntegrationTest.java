package com.example.feniksdemo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CaseApiIntegrationTest.FixedTime.class)
class CaseApiIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-02-03T04:05:06.123456789Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void clearCases() {
        jdbc.update("DELETE FROM cases");
    }

    @Test
    void createAndFetchRoundTripUsesGeneratedIdOpenAndMicrosecondClock() throws Exception {
        var result = http.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Demo case  \"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Demo case"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").value("2026-02-03T04:05:06.123456Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-02-03T04:05:06.123456Z"))
                .andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("id").asLong()).isPositive();
        String location = "/api/cases/" + body.get("id").asLong();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(location);
        var fetched = http.perform(get(location)).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(fetched.getResponse().getContentAsString())).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "O'Brien; SELECT * FROM cases"})
    void titleIsStoredAsData(String title) throws Exception {
        var response = http.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("title", title))))
                .andExpect(status().isCreated()).andReturn().getResponse();
        http.perform(get(response.getHeader("Location")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value(title));
    }

    @Test
    void acceptsTwoHundredCharactersAfterTrimming() throws Exception {
        http.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("title", "  " + "x".repeat(200) + "  "))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("x".repeat(200)));
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "{\"title\":null}", "{\"title\":\"\"}", "{\"title\":\"   \"}",
                "{\"title\":\"" + "x".repeat(201) + "\"}", "{", "", "null", "[]",
                "{\"title\":123}", "{\"title\":1.5}", "{\"title\":true}",
                "{\"title\":\"Demo\"} {}", "{\"title\":{}}", "{\"title\":\"Demo\",\"extra\":true}",
                "{\"title\":\"Demo\",\"id\":1}", "{\"title\":\"Demo\",\"status\":\"CLOSED\"}",
                "{\"title\":\"Demo\",\"createdAt\":\"2020-01-01T00:00:00Z\"}",
                "{\"title\":\"Demo\",\"updatedAt\":null}");
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidRequestsWithoutInserting(String body) throws Exception {
        assertProblem(http.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON).content(body)),
                400, "Bad Request", "/api/cases");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void rejectsInvalidIds(String id) throws Exception {
        assertProblem(http.perform(get("/api/cases/" + id)), 400, "Bad Request", "/api/cases/" + id);
    }

    @Test
    void unknownPositiveIdIsNotFound() throws Exception {
        assertProblem(http.perform(get("/api/cases/9223372036854775807")),
                404, "Not Found", "/api/cases/9223372036854775807");
    }

    private void assertProblem(ResultActions response, int statusCode, String title, String path) throws Exception {
        var result = response.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.instance").value(path)).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("detail").asText()).isNotBlank()
                .doesNotContain("Exception", "SELECT", "INSERT", "org.springframework");
    }
}
