package com.kun.aiinterview.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.auth.dto.LoginRequest;
import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.auth.service.AuthService;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"local", "test"})
class PasswordUtf8BoundaryIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AuthService authService;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenService tokens;

    private String account;
    private final List<String> sensitiveValues = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void setUp() {
        account = "b5pwd_" + UUID.randomUUID().toString().replace("-", "");
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void cleanUpAndCheckLogs() {
        root.detachAppender(logs);
        logs.stop();
        jdbc.update("DELETE FROM `user` WHERE account IN (?, ?)", account, account + "_bad");
        String rendered = logs.list.stream().map(event -> event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? ""
                : ThrowableProxyUtil.asString(event.getThrowableProxy()))).reduce("", String::concat);
        for (String value : sensitiveValues) {
            assertFalse(rendered.contains(value), "logs must not contain test passwords or hashes");
        }
    }

    @ParameterizedTest(name = "accepted boundary {0}")
    @EnumSource(value = PasswordSample.class, names = {"ASCII_MIN", "ASCII_72", "CJK_72", "EMOJI_72", "COMBINING_72", "SPACES", "UNICODE_SPACES"})
    void shouldRegisterLoginAndChangeWithoutRewriting(PasswordSample sample) throws Exception {
        String password = secret(sample.value);
        call(post("/api/auth/register"), registration(account, password), 200);
        User registered = users.getUserByAccount(account);
        secret(registered.getPassword());
        assertTrue(encoder.matches(password, registered.getPassword()));
        String token = login(password);

        // Exercise the sample as both the current and the new password across committed requests.
        String interim = secret(PasswordSample.INTERIM.value);
        call(authorized(put("/api/users/me/password"), token), new ChangePasswordRequest(password, interim), 200);
        call(authorized(get("/api/users/me"), token), null, 401);
        String interimToken = login(interim);
        call(authorized(put("/api/users/me/password"), interimToken), new ChangePasswordRequest(interim, password), 200);
        call(authorized(get("/api/users/me"), interimToken), null, 401);
        call(authorized(get("/api/users/me"), login(password)), null, 200);
        User changed = users.getUserByAccount(account);
        secret(changed.getPassword());
        assertTrue(encoder.matches(password, changed.getPassword()));
        assertFalse(encoder.matches(interim, changed.getPassword()));
        assertNotNull(changed.getPasswordChangedAt());
    }

    @ParameterizedTest(name = "rejected boundary {0}")
    @EnumSource(value = PasswordSample.class, names = {"ASCII_73", "MIXED_73", "CJK_75", "EMOJI_76", "COMBINING_75", "EMPTY", "BLANK", "CONTROL_BLANK", "SHORT", "NULL"})
    void shouldRejectAtEveryHttpBoundaryWithoutWriting(PasswordSample sample) throws Exception {
        String invalid = secret(sample.value);
        String current = secret(PasswordSample.INTERIM.value);
        authService.register(registration(account, current));
        User before = users.getUserByAccount(account);
        secret(before.getPassword());
        String token = login(current);

        assertAll(
                () -> call(post("/api/auth/register"), registration(account + "_bad", invalid), 400),
                () -> assertNull(users.getUserByAccount(account + "_bad")),
                () -> call(post("/api/auth/login"), new LoginRequest(account, invalid), 400),
                () -> call(authorized(put("/api/users/me/password"), token), new ChangePasswordRequest(current, invalid), 400),
                () -> call(authorized(put("/api/users/me/password"), token), new ChangePasswordRequest(invalid, current), 400),
                () -> assertUnchanged(before),
                () -> call(authorized(get("/api/users/me"), token), null, 200)
        );
    }

    @Test
    void shouldRejectOversizedMatchingPrefixInsteadOfAcceptingBcryptAlias() throws Exception {
        String boundary = secret(PasswordSample.CJK_72.value);
        String over = secret(PasswordSample.CJK_75.value);
        authService.register(registration(account, boundary));
        User before = users.getUserByAccount(account);
        // The encoder intentionally retains legacy verification semantics; API policy is stricter.
        assertTrue(encoder.matches(over, before.getPassword()));
        call(post("/api/auth/login"), new LoginRequest(account, over), 400);
        call(authorized(put("/api/users/me/password"), login(boundary)),
                new ChangePasswordRequest(over, secret(PasswordSample.INTERIM.value)), 400);
        assertUnchanged(before);
    }

    @Test
    void shouldStillVerifyALegacyShortCurrentPassword() throws Exception {
        authService.register(registration(account, secret(PasswordSample.INTERIM.value)));
        String shortCurrent = secret(PasswordSample.SHORT.value);
        jdbc.update("UPDATE `user` SET password = ? WHERE account = ?", encoder.encode(shortCurrent), account);
        User legacy = users.getUserByAccount(account);
        String token = tokens.generateAccessToken(legacy.getId(), legacy.getAccount(), legacy.getRole(), legacy.getPassword());
        call(authorized(put("/api/users/me/password"), token),
                new ChangePasswordRequest(shortCurrent, secret(PasswordSample.CJK_72.value)), 200);
        call(authorized(get("/api/users/me"), token), null, 401);
        call(authorized(get("/api/users/me"), login(PasswordSample.CJK_72.value)), null, 200);
    }

    private void assertUnchanged(User before) {
        User after = users.getUserByAccount(account);
        assertTrue(before.getPassword().equals(after.getPassword()), "hash must be unchanged");
        assertEquals(before.getPasswordChangedAt(), after.getPasswordChangedAt());
        assertEquals(before.getUpdatedAt(), after.getUpdatedAt());
    }

    private String login(String password) throws Exception {
        MvcResult result = call(post("/api/auth/login"), new LoginRequest(account, password), 200);
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private MvcResult call(MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        String response = result.getResponse().getContentAsString();
        for (String value : sensitiveValues) {
            assertFalse(response.contains(value), "response must not contain test passwords or hashes");
        }
        assertEquals(expected, result.getResponse().getStatus(), "HTTP status for " + result.getRequest().getRequestURI());
        assertEquals(expected, json.readTree(response).path("code").asInt());
        return result;
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private RegisterRequest registration(String name, String password) {
        return new RegisterRequest(name, "boundary-test", password, name + "@example.com");
    }

    private String secret(String value) {
        if (value != null && !value.isBlank()) sensitiveValues.add(value);
        return value;
    }
}
