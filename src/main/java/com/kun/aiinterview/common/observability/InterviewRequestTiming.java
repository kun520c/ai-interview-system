package com.kun.aiinterview.common.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Request-scoped timing for the two synchronous interview POST endpoints. */
public final class InterviewRequestTiming {

    // This channel carries the fixed timing schema, separately from application diagnostics.
    private static final Logger LOG = LoggerFactory.getLogger("interview.timing");
    private static final String ATTRIBUTE = InterviewRequestTiming.class.getName();

    public enum Operation { ANSWER, REPORT }

    public enum Phase {
        ANSWER_SUBMIT, ANSWER_CLAIM, ANSWER_DB_CONTEXT, QUERY_BUILD, EMBEDDING, MILVUS_SEARCH,
        MYSQL_HYDRATION, ANSWER_PROMPT, ANSWER_DEEPSEEK, ANSWER_VALIDATE, ANSWER_COMMIT,
        REPORT_CLAIM, REPORT_FINAL_AGGREGATION, REPORT_PROMPT, REPORT_DEEPSEEK,
        REPORT_VALIDATE, REPORT_COMMIT
    }

    private final String id = UUID.randomUUID().toString();
    private final long start = System.nanoTime();
    private final Operation operation;
    private final EnumMap<Phase, Long> nanosByPhase = new EnumMap<>(Phase.class);

    private InterviewRequestTiming(Operation operation) {
        this.operation = operation;
    }

    public static String begin(HttpServletRequest request, Operation operation) {
        if (request.getAttribute(ATTRIBUTE) instanceof InterviewRequestTiming current) {
            return current.id;
        }
        var timing = new InterviewRequestTiming(operation);
        request.setAttribute(ATTRIBUTE, timing);
        return timing.id;
    }

    public static <T> T measure(Phase phase, Supplier<T> action) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                || !(attributes.getRequest().getAttribute(ATTRIBUTE) instanceof InterviewRequestTiming timing)) {
            return action.get();
        }
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            timing.nanosByPhase.merge(phase, System.nanoTime() - started, Long::sum);
        }
    }

    public static void measure(Phase phase, Runnable action) {
        measure(phase, () -> {
            action.run();
            return null;
        });
    }

    public static void finish(HttpServletRequest request, int httpStatus, boolean unhandledError) {
        if (!(request.getAttribute(ATTRIBUTE) instanceof InterviewRequestTiming timing)) {
            return;
        }
        long durationUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - timing.start);
        request.removeAttribute(ATTRIBUTE);
        var phasesUs = new EnumMap<Phase, Long>(Phase.class);
        timing.nanosByPhase.forEach((phase, nanos) -> phasesUs.put(phase, TimeUnit.NANOSECONDS.toMicros(nanos)));
        LOG.info("interview_timing operation={} timingId={} httpStatus={} unhandledError={} durationUs={} phasesUs={}",
                timing.operation, timing.id, httpStatus, unhandledError, durationUs, phasesUs);
    }
}
