package com.adela.payments.integration;

import com.adela.payments.entity.Role;
import com.adela.payments.entity.User;
import com.adela.payments.repository.PaymentRepository;
import com.adela.payments.repository.RoleRepository;
import com.adela.payments.repository.UserRepository;
import com.adela.payments.request.RegistrationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-stack integration tests: real Postgres + Redis via Testcontainers, real
 * Liquibase migrations, real Spring Security/JWT filter chain, driven through MockMvc.
 * <p>
 * Containers are started once in a static initializer (the "singleton container" pattern) instead
 * of via {@code @Testcontainers}/{@code @Container}, because those annotations stop the container
 * in each subclass's {@code @AfterAll} - since the static fields live on this shared superclass,
 * that would tear the containers down after the first subclass's tests finish and break the rest.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String REDIS_PASSWORD = "test-secret";

    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("payments")
                .withUsername("postgres")
                .withPassword("postgres");
        POSTGRES.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected static final String DEFAULT_PASSWORD = "Password123!";

    protected void register(String email, String phoneNumber) throws Exception {
        RegistrationRequest request = RegistrationRequest.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .phoneNumber(phoneNumber)
                .password(DEFAULT_PASSWORD)
                .confirmPassword(DEFAULT_PASSWORD)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    protected String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        return json.get("access_token").asText();
    }

    protected String registerAndLogin(String email, String phoneNumber) throws Exception {
        register(email, phoneNumber);
        return login(email, DEFAULT_PASSWORD);
    }

    protected void promoteToAdmin(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRoles(List.of(adminRole));
        userRepository.save(user);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
