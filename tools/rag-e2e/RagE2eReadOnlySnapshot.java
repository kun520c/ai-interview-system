import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class RagE2eReadOnlySnapshot {
    static List<Map<String,String>> select(Connection c,String sql,Object... args) throws Exception {
        if(!sql.startsWith("SELECT "))throw new SecurityException("SELECT_ONLY");
        try(var s=c.prepareStatement(sql)) {
            s.setQueryTimeout(8);s.setMaxRows(1000);
            for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);
            try(var rs=s.executeQuery()) {
                var out=new ArrayList<Map<String,String>>();var md=rs.getMetaData();
                while(rs.next()) {
                    var row=new LinkedHashMap<String,String>();
                    for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i),rs.getString(i));
                    out.add(row);
                }
                return out;
            }
        }
    }
    public static void main(String[] args) throws Exception {
        RagE2eRunner.loadContract();
        var result=new LinkedHashMap<String,Object>();
        String url="jdbc:mysql://127.0.0.1:3306/ai_interview_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        try(var c=DriverManager.getConnection(url,System.getenv("DIAG_DB_USERNAME"),System.getenv("DIAG_DB_PASSWORD"))) {
            c.setReadOnly(true);c.setAutoCommit(false);
            var identity=select(c,"SELECT DATABASE() AS db,@@server_uuid AS uuid,@@transaction_read_only AS read_only").getFirst();
            if(!"ai_interview_test".equals(identity.get("db")) || !"6ccac806-1bb1-11f1-b45f-107c61ba59a5".equals(identity.get("uuid")) || !"1".equals(identity.get("read_only")))throw new SecurityException("TARGET_READ_ONLY_REQUIRED");
            result.put("identity",identity);
            result.put("users",select(c,"SELECT id,role,status FROM user WHERE id IN (360,361) ORDER BY id"));
            result.put("sessions",select(c,"SELECT * FROM interview_session WHERE id=139 AND user_id=361"));
            result.put("questions",select(c,"SELECT * FROM interview_question WHERE session_id=139 ORDER BY display_order,id"));
            result.put("answers",select(c,"SELECT a.* FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=139 ORDER BY a.id"));
            result.put("evaluations",select(c,"SELECT e.* FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=139 ORDER BY e.id"));
            result.put("batches",select(c,"SELECT b.* FROM rag_retrieval_batch b JOIN interview_answer a ON a.id=b.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=139 ORDER BY b.retrieval_batch_id"));
            result.put("hits",select(c,"SELECT h.*,c.document_id,c.document_version,c.chunk_index,c.vector_id,c.content,c.status AS chunk_status,c.embedding_model AS chunk_embedding_model,c.embedding_version AS chunk_embedding_version,d.title,d.category,d.source,d.processing_status,d.document_version AS current_document_version FROM rag_hit_log h JOIN interview_answer a ON a.id=h.answer_id JOIN interview_question q ON q.id=a.interview_question_id LEFT JOIN knowledge_chunk c ON c.id=h.chunk_id LEFT JOIN knowledge_document d ON d.id=c.document_id WHERE q.session_id=139 ORDER BY h.rank_no"));
            result.put("jsonColumn",select(c,"SELECT DATA_TYPE,COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='answer_evaluation' AND COLUMN_NAME='raw_result'"));
            result.put("reports",select(c,"SELECT id FROM interview_report WHERE session_id=139"));
            result.put("weaknesses",select(c,"SELECT id FROM user_weakness WHERE user_id=361"));
            result.put("corpusCounts",select(c,"SELECT (SELECT COUNT(*) FROM question) AS question,(SELECT COUNT(*) FROM question_scoring_point) AS scoring_points,(SELECT COUNT(*) FROM knowledge_document) AS documents,(SELECT COUNT(*) FROM knowledge_chunk) AS chunks"));
            var corpus=new LinkedHashMap<String,List<Map<String,String>>>();
            var fingerprints=new LinkedHashMap<String,Object>();
            for(String table:RagE2ePolicy.CORPUS) {
                var rows=RagE2eRunner.corpus(c,table);
                String sha=RagE2ePolicy.rowHash(rows);
                var expected=RagE2eRunner.contract.path("mysqlFrozen").path(table);
                RagE2ePolicy.require(rows.size()==expected.path("count").asInt()
                        && sha.equals(expected.path("rowSnapshotSha256").asText()),"EXISTING_CORPUS_CHANGED");
                corpus.put(table,rows);
                fingerprints.put(table,Map.of("rows",rows.size(),"rowSnapshotSha256",sha));
            }
            RagE2ePolicy.corpusProfile(corpus.get("knowledge_document"),corpus.get("knowledge_chunk"));
            result.put("corpusFingerprints",fingerprints);
            result.put("all52ChunksEligible",true);
            result.put("hydrationDigestSummary",select(c,"SELECT DIGEST_TEXT,COUNT_STAR,SUM_ROWS_SENT,FIRST_SEEN,LAST_SEEN FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='ai_interview_test' AND DIGEST_TEXT LIKE '%kc%vector_id%IN%' AND DIGEST_TEXT LIKE 'SELECT%' ORDER BY LAST_SEEN"));
            var original=RagE2eRunner.JSON.readTree(Path.of("target/rag-e2e/attempts/"+RagE2eRunner.RECOVERY_RUN_ID+"/report.json").toFile());
            var saved=(List<Map<String,String>>)result.get("evaluations");
            RagE2ePolicy.require(saved.size()==1,"ONE_EXISTING_EVALUATION_REQUIRED");
            String providerContent=RagE2eReadOnlyVerifier.recoverProviderContent(saved.getFirst().get("raw_result"),
                    RagE2eReadOnlyVerifier.modelEvidence(original,"deepseek").path("rawEvaluationSha256").asText());
            String normalized=select(c,"SELECT CAST(? AS JSON) AS normalized",providerContent).getFirst().get("normalized");
            result.put("mysqlCastExactlyEqualsStoredRawResult",normalized.equals(saved.getFirst().get("raw_result")));
            c.rollback();
        }
        String json=new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result);
        Files.writeString(Path.of(args[0]),json,StandardOpenOption.CREATE_NEW);
        System.out.println("READ_ONLY_SNAPSHOT_SAVED; modelCalls=0; businessHttp=0; databaseWrites=0");
    }
}
