package com.kun.aiinterview.knowledge.chunk;

import com.kun.aiinterview.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeTextChunker {

    private static final int MAX_CHUNK_CHARACTERS = 1200;
    private static final int OVERLAP_CHARACTERS = 150;
    private static final int MIN_NATURAL_BOUNDARY_DISTANCE = 800;

    public List<KnowledgeChunkDraft> split(String content){
        if(content == null || content.isBlank()){
            throw new BusinessException("待切片文档正文不能为空");
        }

        List<KnowledgeChunkDraft> chunks = new ArrayList<>();

        int start = 0;
        int chunkIndex = 1;

        while(start < content.length()){
            int hardEnd = Math.min(
                    start + MAX_CHUNK_CHARACTERS,
                    content.length());

            int end = findNaturalBoundary(
                    content,
                    start,
                    hardEnd
            );

            if(end <= start){
                end = hardEnd;
            }

            String chunkContent = content
                    .substring(start, end)
                    .strip();

            if(!chunkContent.isEmpty()){
                chunks.add(
                        new KnowledgeChunkDraft(
                                chunkIndex,
                                chunkContent,
                                chunkContent.length()
                        )
                );

                chunkIndex++;
            }

            if(end >= content.length()){
                break;
            }

            int nextStart = calculateNextStart(
                    content,
                    start,
                    end
            );

            if (nextStart <= start) {
                nextStart = end;
            }

            start = nextStart;
        }

        if(chunks.isEmpty()){
            throw new BusinessException("文档切片结果不能为空");
        }

        return chunks;
    }

    private int findNaturalBoundary(
            String content,
            int start,
            int hardEnd
    ){
        if(hardEnd >= content.length()){
            return content.length();
        }

        int minimumBoundary = Math.min(
                start + MIN_NATURAL_BOUNDARY_DISTANCE,
                hardEnd
        );

        int paragraphBoundary = content.lastIndexOf(
                "\n\n",
                hardEnd - 2
        );

        if(paragraphBoundary >= minimumBoundary){
            return paragraphBoundary + 2;
        }

        for(int index = hardEnd - 1;index >= minimumBoundary;index--){
            if(content.charAt(index) == '\n'){
                return index + 1;
            }
        }

        for(int index = hardEnd - 1;index >= minimumBoundary;index--){
            if(isSentenceBoundary(content.charAt(index))){
                return index + 1;
            }
        }

        return hardEnd;
    }

    private boolean isSentenceBoundary(char character){
        return character == '。'
                || character == '！'
                || character == '？'
                || character == '；'
                || character == '.'
                || character == '?'
                || character == '!'
                || character == ';';
    }

    private int calculateNextStart(
            String content,
            int currentStart,
            int currentEnd
    ){
        int candidate = Math.max(
                currentStart + 1,
                currentEnd - OVERLAP_CHARACTERS
        );

        int adjusted = candidate;

        while(adjusted < currentEnd
                && !Character.isWhitespace(
                        content.charAt(adjusted)
        )){
            adjusted++;
        }

        while (adjusted < currentEnd
                    && Character.isWhitespace(
                            content.charAt(adjusted)
        ))  {
            adjusted++;
        }

        if(adjusted >= currentEnd){
            return candidate;
        }

        return adjusted;
    }
}
