package com.kun.aiinterview.interview.vo;

import java.util.List;

public record InterviewHistoryPageResponse(

        int page,

        int pageSize,

        long total,

        long totalPages,

        List<InterviewHistoryItemResponse> items

) {
}
