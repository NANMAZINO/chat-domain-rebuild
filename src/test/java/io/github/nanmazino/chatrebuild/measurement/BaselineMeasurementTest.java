package io.github.nanmazino.chatrebuild.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.chat.service.ChatRoomService;
import io.github.nanmazino.chatrebuild.global.security.JwtTokenProvider;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "baseline.measurement.enabled", matches = "true")
class BaselineMeasurementTest extends IntegrationTestSupport {

    // The current baseline room list resolves all active rooms before pagination.
    // Keep room count fixed so later measurements stay comparable.
    private static final int ROOM_COUNT = 80;
    private static final int REGULAR_ROOM_MESSAGE_COUNT = 50;
    private static final int TARGET_ROOM_MESSAGE_COUNT = 10_000;
    private static final int ROOM_LIST_PAGE_SIZE = 20;
    private static final int HISTORY_PAGE_SIZE = 30;
    private static final int WARMUP_ITERATIONS = 15;
    private static final int MEASUREMENT_ITERATIONS = 60;
    private static final int MEASUREMENT_ROUNDS = 3;
    private static final int QUERY_COUNT_ITERATIONS = 10;
    private static final int MESSAGE_BATCH_SIZE = 500;
    private static final double ALLOWED_ROOM_LIST_P95_DEVIATION_RATIO = 0.30;
    private static final double ALLOWED_HISTORY_P95_DEVIATION_RATIO = 0.30;
    private static final long ALLOWED_QUERY_COUNT_DEVIATION = 0L;
    private static final Path REPORT_PATH = Path.of("docs", "performance", "measurements", "latest-measurement.md");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private MeasurementFixture fixture;

    @BeforeAll
    void setUpDataset() {
        cleanDatabase();

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        fixture = seedMeasurementDataset();
    }

    @AfterAll
    void tearDownDataset() {
        cleanDatabase();
    }

    @Test
    @DisplayName("baseline 측정용 데이터셋과 반복 측정 리포트를 생성할 수 있다")
    void baselineMeasurementIsRepeatable() throws Exception {
        EndpointStability roomListStability = measureStability(this::measureRoomListEndpoint);
        EndpointStability historyLatestStability = measureStability(this::measureMessageHistoryLatestEndpoint);
        EndpointStability historyCursorStability = measureStability(this::measureMessageHistoryCursorEndpoint);
        QueryCountMeasurement roomListQueryCount = measureRoomListQueryCount();
        boolean strictStabilityCheckEnabled = Boolean.getBoolean("baseline.measurement.strict");

        writeReport(
            roomListStability,
            historyLatestStability,
            historyCursorStability,
            roomListQueryCount,
            strictStabilityCheckEnabled
        );

        if (strictStabilityCheckEnabled) {
            assertP95StabilityWithinRange(roomListStability, ALLOWED_ROOM_LIST_P95_DEVIATION_RATIO);
            assertP95StabilityWithinRange(historyLatestStability, ALLOWED_HISTORY_P95_DEVIATION_RATIO);
            assertP95StabilityWithinRange(historyCursorStability, ALLOWED_HISTORY_P95_DEVIATION_RATIO);
        }
        assertThat(roomListQueryCount.maxDeviation())
            .withFailMessage(
                "room list query count 편차가 허용 범위를 넘었습니다. counts=%s, maxDeviation=%d. 상세 측정값은 docs/performance/measurements/latest-measurement.md를 확인하세요.",
                roomListQueryCount.counts(),
                roomListQueryCount.maxDeviation()
            )
            .isLessThanOrEqualTo(ALLOWED_QUERY_COUNT_DEVIATION);
    }

    private MeasurementFixture seedMeasurementDataset() {
        User author = userRepository.save(new User(
            "baseline-author@example.com",
            passwordEncoder.encode("Password123!"),
            "baseline-author"
        ));
        User measurementUser = userRepository.save(new User(
            "baseline-measure@example.com",
            passwordEncoder.encode("Password123!"),
            "baseline-measure"
        ));
        User participant = userRepository.save(new User(
            "baseline-participant@example.com",
            passwordEncoder.encode("Password123!"),
            "baseline-participant"
        ));

        Long targetRoomId = null;

        for (int roomIndex = 1; roomIndex <= ROOM_COUNT; roomIndex++) {
            Post post = postRepository.save(new Post(
                author,
                "Baseline room " + roomIndex,
                "Baseline measurement dataset room " + roomIndex,
                10,
                PostStatus.OPEN
            ));
            ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

            addActiveMember(room, author);
            addActiveMember(room, measurementUser);
            addActiveMember(room, participant);
            chatRoomRepository.saveAndFlush(room);

            boolean isTargetRoom = roomIndex == ROOM_COUNT;
            createMessages(
                room,
                author,
                participant,
                roomIndex,
                isTargetRoom ? TARGET_ROOM_MESSAGE_COUNT : REGULAR_ROOM_MESSAGE_COUNT
            );

            if (isTargetRoom) {
                targetRoomId = room.getId();
            }
        }

        assertThat(targetRoomId).isNotNull();

        return new MeasurementFixture(
            measurementUser.getId(),
            targetRoomId,
            resolveHistoryCursorMessageId(targetRoomId),
            jwtTokenProvider.generateAccessToken(measurementUser.getId(), measurementUser.getEmail())
        );
    }

    private void addActiveMember(ChatRoom room, User user) {
        chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            user,
            ChatRoomMemberStatus.ACTIVE,
            LocalDateTime.now()
        ));
        room.increaseMemberCount();
    }

    private void createMessages(ChatRoom room, User author, User participant, int roomIndex, int messageCount) {
        List<ChatMessage> batch = new ArrayList<>(MESSAGE_BATCH_SIZE);
        ChatMessage latestMessage = null;

        for (int messageIndex = 1; messageIndex <= messageCount; messageIndex++) {
            User sender = messageIndex % 2 == 0 ? author : participant;
            batch.add(new ChatMessage(
                room,
                sender,
                "baseline-room-" + roomIndex + "-message-" + messageIndex,
                ChatMessageType.TEXT
            ));

            if (batch.size() == MESSAGE_BATCH_SIZE) {
                latestMessage = saveMessageBatch(batch);
            }
        }

        if (!batch.isEmpty()) {
            latestMessage = saveMessageBatch(batch);
        }

        if (latestMessage != null) {
            room.updateLastMessageSummary(
                latestMessage.getId(),
                latestMessage.getContent(),
                latestMessage.getCreatedAt()
            );
            chatRoomRepository.saveAndFlush(room);
        }
    }

    private ChatMessage saveMessageBatch(List<ChatMessage> batch) {
        List<ChatMessage> savedBatch = chatMessageRepository.saveAll(batch);
        ChatMessage latestMessage = savedBatch.get(savedBatch.size() - 1);
        batch.clear();
        return latestMessage;
    }

    private Long resolveHistoryCursorMessageId(Long roomId) {
        List<ChatMessage> firstPagePlusOne = chatMessageRepository.findRecentMessages(
            roomId,
            PageRequest.of(0, HISTORY_PAGE_SIZE + 1)
        );

        assertThat(firstPagePlusOne.size())
            .withFailMessage("cursor 측정용 대상 방은 최소 %d개 메시지가 필요합니다.", HISTORY_PAGE_SIZE + 1)
            .isGreaterThan(HISTORY_PAGE_SIZE);

        return firstPagePlusOne.get(HISTORY_PAGE_SIZE - 1).getId();
    }

    private EndpointStability measureStability(MeasurementAction action) throws Exception {
        List<EndpointMeasurement> rounds = new ArrayList<>(MEASUREMENT_ROUNDS);

        for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
            rounds.add(action.measure());
        }

        return EndpointStability.of(rounds);
    }

    private EndpointMeasurement measureRoomListEndpoint() throws Exception {
        warmUp(this::performRoomListRequest);
        return measureEndpoint("GET /api/chat-rooms", this::performRoomListRequest);
    }

    private EndpointMeasurement measureMessageHistoryLatestEndpoint() throws Exception {
        warmUp(this::performMessageHistoryLatestRequest);
        return measureEndpoint("GET /api/chat-rooms/{roomId}/messages (latest page)", this::performMessageHistoryLatestRequest);
    }

    private EndpointMeasurement measureMessageHistoryCursorEndpoint() throws Exception {
        warmUp(this::performMessageHistoryCursorRequest);
        return measureEndpoint("GET /api/chat-rooms/{roomId}/messages (cursor page)", this::performMessageHistoryCursorRequest);
    }

    private void warmUp(RequestAction requestAction) throws Exception {
        for (int index = 0; index < WARMUP_ITERATIONS; index++) {
            requestAction.run();
        }
    }

    private EndpointMeasurement measureEndpoint(String label, RequestAction requestAction) throws Exception {
        List<Long> samples = new ArrayList<>(MEASUREMENT_ITERATIONS);

        for (int index = 0; index < MEASUREMENT_ITERATIONS; index++) {
            long startedAt = System.nanoTime();
            requestAction.run();
            samples.add(System.nanoTime() - startedAt);
        }

        return EndpointMeasurement.of(label, samples);
    }

    private void performRoomListRequest() throws Exception {
        mockMvc.perform(get("/api/chat-rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.accessToken())
                .param("size", String.valueOf(ROOM_LIST_PAGE_SIZE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(ROOM_LIST_PAGE_SIZE))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    private void performMessageHistoryLatestRequest() throws Exception {
        mockMvc.perform(get("/api/chat-rooms/" + fixture.targetRoomId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.accessToken())
                .param("size", String.valueOf(HISTORY_PAGE_SIZE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(HISTORY_PAGE_SIZE))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    private void performMessageHistoryCursorRequest() throws Exception {
        mockMvc.perform(get("/api/chat-rooms/" + fixture.targetRoomId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.accessToken())
                .param("size", String.valueOf(HISTORY_PAGE_SIZE))
                .param("cursorMessageId", String.valueOf(fixture.historyCursorMessageId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(HISTORY_PAGE_SIZE))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    private QueryCountMeasurement measureRoomListQueryCount() {
        List<Long> counts = new ArrayList<>(QUERY_COUNT_ITERATIONS);

        for (int index = 0; index < QUERY_COUNT_ITERATIONS; index++) {
            statistics.clear();
            chatRoomService.getChatRooms(fixture.measurementUserId(), null, null, ROOM_LIST_PAGE_SIZE, null);
            counts.add(statistics.getPrepareStatementCount());
        }

        long baselineCount = counts.get(0);
        long maxDeviation = counts.stream()
            .mapToLong(count -> Math.abs(count - baselineCount))
            .max()
            .orElse(0L);

        return new QueryCountMeasurement(counts, maxDeviation);
    }

    private void assertP95StabilityWithinRange(EndpointStability stability, double allowedDeviationRatio) {
        assertThat(stability.deviationRatio())
            .withFailMessage(
                "%s p95 편차가 허용 범위를 넘었습니다. min=%.3fms, median=%.3fms, max=%.3fms, deviation=%.2f%%, allowed=%.2f%%. 상세 측정값은 docs/performance/measurements/latest-measurement.md를 확인하세요.",
                stability.label(),
                toMillis(stability.minP95Nanos()),
                toMillis(stability.medianP95Nanos()),
                toMillis(stability.maxP95Nanos()),
                stability.deviationRatio() * 100.0,
                allowedDeviationRatio * 100.0
            )
            .isLessThanOrEqualTo(allowedDeviationRatio);
    }

    private void writeReport(
        EndpointStability roomListStability,
        EndpointStability historyLatestStability,
        EndpointStability historyCursorStability,
        QueryCountMeasurement roomListQueryCount,
        boolean strictStabilityCheckEnabled
    ) throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, buildReport(
            roomListStability,
            historyLatestStability,
            historyCursorStability,
            roomListQueryCount,
            strictStabilityCheckEnabled
        ), StandardCharsets.UTF_8);
    }

    private String buildReport(
        EndpointStability roomListStability,
        EndpointStability historyLatestStability,
        EndpointStability historyCursorStability,
        QueryCountMeasurement roomListQueryCount,
        boolean strictStabilityCheckEnabled
    ) {
        return """
            # Chat Measurement Report (Baseline Harness)

            - generatedAt: %s
            - javaVersion: %s
            - os: %s %s
            - availableProcessors: %d
            - maxMemoryMiB: %d
            - reportPath: %s
            - dataset:
              - activeRoomCount: %d
              - regularRoomMessageCount: %d
              - targetRoomMessageCount: %d
              - roomListPageSize: %d
              - historyPageSize: %d
            - iterations:
              - warmup: %d
              - measurement: %d
              - rounds: %d
              - queryCount: %d
            - allowedDeviation:
              - roomListP95: %.0f%%
              - messageHistoryP95: %.0f%%
              - roomListQueryCount: %d
            - strictStabilityCheckEnabled: %s
            - baselineNotes:
              - this is the latest raw result measured with the fixed baseline dataset and iteration rules.
              - keep tracked snapshots under `docs/performance/measurements` for official comparisons and archives.
              - overwrite this file on each local rerun and do not use it as the official before or after reference.
              - default run records p95 stability as PASS/WARN and does not fail the task.
              - use `-Dbaseline.measurement.strict=true` if you want p95 stability to fail the task.

            ## Room List p95

            %s

            ## Message History p95 (Latest Page)

            %s

            ## Message History p95 (Cursor Page)

            %s

            ## Room List Query Count

            - counts: %s
            - maxDeviation: %d
            - note: query count is measured from `ChatRoomService#getChatRooms` using Hibernate prepareStatementCount.
            """.formatted(
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / 1024 / 1024,
            REPORT_PATH.toString().replace('\\', '/'),
            ROOM_COUNT,
            REGULAR_ROOM_MESSAGE_COUNT,
            TARGET_ROOM_MESSAGE_COUNT,
            ROOM_LIST_PAGE_SIZE,
            HISTORY_PAGE_SIZE,
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            MEASUREMENT_ROUNDS,
            QUERY_COUNT_ITERATIONS,
            ALLOWED_ROOM_LIST_P95_DEVIATION_RATIO * 100.0,
            ALLOWED_HISTORY_P95_DEVIATION_RATIO * 100.0,
            ALLOWED_QUERY_COUNT_DEVIATION,
            strictStabilityCheckEnabled,
            roomListStability.toMarkdown(ALLOWED_ROOM_LIST_P95_DEVIATION_RATIO),
            historyLatestStability.toMarkdown(ALLOWED_HISTORY_P95_DEVIATION_RATIO),
            historyCursorStability.toMarkdown(ALLOWED_HISTORY_P95_DEVIATION_RATIO),
            roomListQueryCount.counts(),
            roomListQueryCount.maxDeviation()
        );
    }

    private void cleanDatabase() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record MeasurementFixture(
        Long measurementUserId,
        Long targetRoomId,
        Long historyCursorMessageId,
        String accessToken
    ) {
    }

    private record QueryCountMeasurement(
        List<Long> counts,
        long maxDeviation
    ) {
    }

    private record EndpointMeasurement(
        String label,
        int sampleCount,
        long minNanos,
        long averageNanos,
        long p95Nanos,
        long maxNanos
    ) {

        private static EndpointMeasurement of(String label, List<Long> rawSamplesNanos) {
            List<Long> sortedSamples = rawSamplesNanos.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
            LongSummaryStatistics summary = rawSamplesNanos.stream()
                .collect(Collectors.summarizingLong(Long::longValue));
            int p95Index = Math.max(0, (int) Math.ceil(sortedSamples.size() * 0.95) - 1);

            return new EndpointMeasurement(
                label,
                rawSamplesNanos.size(),
                summary.getMin(),
                Math.round(summary.getAverage()),
                sortedSamples.get(p95Index),
                summary.getMax()
            );
        }

        private String toMarkdown(String roundName) {
            return """
                - %s
                  - label: %s
                  - sampleCount: %d
                  - minMs: %.3f
                  - avgMs: %.3f
                  - p95Ms: %.3f
                  - maxMs: %.3f
                """.formatted(
                roundName,
                label,
                sampleCount,
                minNanos / 1_000_000.0,
                averageNanos / 1_000_000.0,
                p95Nanos / 1_000_000.0,
                maxNanos / 1_000_000.0
            );
        }
    }

    private record EndpointStability(
        String label,
        List<EndpointMeasurement> rounds,
        long minP95Nanos,
        long medianP95Nanos,
        long maxP95Nanos,
        double deviationRatio
    ) {

        private static EndpointStability of(List<EndpointMeasurement> rounds) {
            assertThat(rounds).isNotEmpty();

            List<Long> p95Samples = rounds.stream()
                .map(EndpointMeasurement::p95Nanos)
                .sorted()
                .toList();
            long minP95Nanos = p95Samples.get(0);
            long medianP95Nanos = p95Samples.get(p95Samples.size() / 2);
            long maxP95Nanos = p95Samples.get(p95Samples.size() - 1);
            double deviationRatio = maxP95Nanos == 0L
                ? 0.0
                : (maxP95Nanos - minP95Nanos) / (double) maxP95Nanos;

            return new EndpointStability(
                rounds.get(0).label(),
                List.copyOf(rounds),
                minP95Nanos,
                medianP95Nanos,
                maxP95Nanos,
                deviationRatio
            );
        }

        private String toMarkdown(double allowedDeviationRatio) {
            StringBuilder builder = new StringBuilder();

            for (int index = 0; index < rounds.size(); index++) {
                builder.append(rounds.get(index).toMarkdown("round-" + (index + 1)));
                if (index < rounds.size() - 1) {
                    builder.append(System.lineSeparator()).append(System.lineSeparator());
                }
            }

            builder.append(System.lineSeparator()).append(System.lineSeparator())
                .append("- stability").append(System.lineSeparator())
                .append("  - minP95Ms: ").append(String.format("%.3f", minP95Nanos / 1_000_000.0)).append(System.lineSeparator())
                .append("  - medianP95Ms: ").append(String.format("%.3f", medianP95Nanos / 1_000_000.0)).append(System.lineSeparator())
                .append("  - maxP95Ms: ").append(String.format("%.3f", maxP95Nanos / 1_000_000.0)).append(System.lineSeparator())
                .append("  - deviationPercent: ").append(String.format("%.2f", deviationRatio * 100.0)).append(System.lineSeparator())
                .append("  - allowedDeviationPercent: ").append(String.format("%.2f", allowedDeviationRatio * 100.0)).append(System.lineSeparator())
                .append("  - status: ").append(deviationRatio <= allowedDeviationRatio ? "PASS" : "WARN");

            return builder.toString();
        }
    }

    @FunctionalInterface
    private interface MeasurementAction {

        EndpointMeasurement measure() throws Exception;
    }

    @FunctionalInterface
    private interface RequestAction {

        void run() throws Exception;
    }
}
