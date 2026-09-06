package com.example.feniksdemo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StatusApiIntegrationTest.Time.class)
class StatusApiIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestClock clock;

    static class TestClock extends Clock {
        Instant now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Time {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM cases");
        clock.now = Instant.parse("2026-03-01T00:00:00.123456789Z");
    }

    static Stream<Arguments> transitions() {
        return Stream.of(CaseStatus.values()).flatMap(from -> Stream.of(CaseStatus.values())
                .map(to -> Arguments.of(from, to)));
    }

    private String create() throws Exception {
        return http.perform(post("/api/cases").contentType("application/json").content("{\"title\":\"Status test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void supportsEveryTransitionAndPreservesNoOpTimestamps(CaseStatus from, CaseStatus to) throws Exception {
        String path = create();
        clock.now = Instant.parse("2026-03-02T00:00:00.234567891Z");
        String before = http.perform(patch(path + "/status").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("status", from))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        clock.now = Instant.parse("2026-03-03T00:00:00.345678912Z");
        String updated = http.perform(patch(path + "/status").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("status", to))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(to.name()))
                .andExpect(jsonPath("$.title").value("Status test"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-01T00:00:00.123456Z"))
                .andReturn().getResponse().getContentAsString();
        var body = json.readTree(updated);
        assertThat(body.get("updatedAt").asText()).isEqualTo(from == to
                ? json.readTree(before).get("updatedAt").asText() : "2026-03-03T00:00:00.345678Z");
        assertThat(json.readTree(http.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}",
            "{\"status\":\"open\"}", "{\"status\":0}", "{", "null",
            "{\"status\":\"CLOSED\",\"title\":\"Changed\"}"})
    void rejectsInvalidStatusBodiesWithoutChangingCase(String body) throws Exception {
        String path = create();
        var before = http.perform(get(path)).andReturn().getResponse().getContentAsString();
        http.perform(patch(path + "/status").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path + "/status"));
        assertThat(http.perform(get(path)).andReturn().getResponse().getContentAsString()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "x", "1.5", "9223372036854775808"})
    void rejectsInvalidId(String id) throws Exception {
        http.perform(patch("/api/cases/" + id + "/status").contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void absentCaseReturnsProblem() throws Exception {
        http.perform(patch("/api/cases/9223372036854775807/status").contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Case not found."));
    }
}
