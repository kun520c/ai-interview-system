package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JevClientTest {
    private MockRestServiceServer server;
    private JevClient client;
    private final JevRequest request = new JevRequest("jev-latest", "test answer",
            Map.of("accuracy", new JevRequest.Question("score", "rate accuracy", List.of("poor", "good"))));

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://localhost")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JevClient(builder.build(), new ObjectMapper());
    }

    @AfterEach
    void verifyHttp() {
        server.verify();
    }

    @Test
    void parsesOfficialScoreResponseAndSendsOfficialHttpContract() {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("jev-latest"))
                .andExpect(jsonPath("$.state").value("test answer"))
                .andExpect(jsonPath("$.questions.accuracy.criteria[1]").value("good"))
                .andRespond(withSuccess("""
                        {"model":"jev-1.13.0","answers":{"accuracy":{"type":"score","score":0.75,
                        "legend":{"0":"poor","1":"good"},"probabilities":{"0":0.25,"1":0.75},
                        "confidence":0.5}},"usage":{"input_tokens":100,"output_tokens":10}}
                        """, MediaType.APPLICATION_JSON));
        var response = client.evaluate(request);
        assertThat(response.model()).isEqualTo("jev-1.13.0");
        assertThat(response.answers().get("accuracy").score()).isEqualTo(0.75);
        assertThat(response.answers().get("accuracy").probabilities()).containsEntry("1", 0.75);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{bad", "{}", "{\"model\":\"jev\",\"answers\":{}}",
            "{\"model\":\"jev\",\"answers\":{\"accuracy\":null}}",
            "{\"model\":\"jev\",\"answers\":{\"accuracy\":{},\"extra\":{}}}",
            "{\"model\":\"jev\",\"answers\":{\"accuracy\":{\"score\":\"0.5\"}}}",
            "{\"model\":\"jev\",\"model\":\"duplicate\",\"answers\":{\"accuracy\":{}}}",
            "{\"model\":\"jev\",\"answers\":{\"accuracy\":{}}} {}"})
    void rejectsInvalidResponseEnvelopeAndJson(String body) {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.evaluate(request)).isInstanceOf(ExternalServiceException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 422, 429, 500, 529})
    void sanitizesHttpErrorsWithoutExposingProviderBody(int status) {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("private-answer-and-key"));
        var error = catchThrowable(() -> client.evaluate(request));
        assertThat(error).isInstanceOf(ExternalServiceException.class)
                .hasMessage("Jev HTTP调用失败").hasNoCause();
    }

    @Test
    void convertsTimeoutToSanitizedExternalFailure() {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(httpRequest -> { throw new SocketTimeoutException("private details"); });
        assertThatThrownBy(() -> client.evaluate(request)).isInstanceOf(ExternalServiceException.class)
                .hasMessage("Jev HTTP或JSON解析失败").hasNoCause();
    }
}
