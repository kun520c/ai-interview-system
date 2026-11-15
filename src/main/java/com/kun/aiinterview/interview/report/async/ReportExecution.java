package com.kun.aiinterview.interview.report.async;

import com.kun.aiinterview.interview.entity.InterviewSession;

/** Captured once from the winning CAS. Completion never substitutes a newer DB version. */
public record ReportExecution(ReportTaskMessage message, int executionVersion, InterviewSession session) {}
