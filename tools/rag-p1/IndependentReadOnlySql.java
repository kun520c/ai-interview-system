import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/** Fixed SELECTs behind a server-enforced read-only transaction. No caller-supplied SQL. */
final class IndependentReadOnlySql implements AutoCloseable {
    static final String IDENTITY = "SELECT DATABASE() AS db, CURRENT_USER() AS account, CURRENT_ROLE() AS roles, "
            + "@@mandatory_roles AS mandatory, @@server_uuid AS uuid, @@session.transaction_read_only AS read_only";
    static final String PRIVILEGES = "SELECT TABLE_NAME,PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.TABLE_PRIVILEGES "
            + "WHERE GRANTEE = ? AND TABLE_SCHEMA = 'ai_interview_test' ORDER BY TABLE_NAME,PRIVILEGE_TYPE";
    static final String SCHEMA_PRIVILEGES = "SELECT PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.SCHEMA_PRIVILEGES "
            + "WHERE GRANTEE = ? AND TABLE_SCHEMA = 'ai_interview_test' ORDER BY PRIVILEGE_TYPE";
    static final String HYDRATE = "SELECT kc.id AS chunkId,kc.document_id AS documentId,kc.document_version AS documentVersion,"
            + "kc.chunk_index AS chunkIndex,kc.vector_id AS vectorId,kc.content AS content,"
            + "kd.title AS title,kd.category AS category,kd.source AS source FROM knowledge_chunk kc "
            + "INNER JOIN knowledge_document kd ON kd.id=kc.document_id WHERE kc.vector_id IN (%s) "
            + "AND kc.status = 'ACTIVE' AND kd.processing_status = 'READY' AND kc.document_version = kd.document_version "
            + "AND kc.embedding_model = ? AND kc.embedding_version = ?";
    static final String LOOKUP = "SELECT kc.id AS chunkId,kc.document_id AS documentId,kc.document_version AS documentVersion,"
            + "kc.chunk_index AS chunkIndex,kc.vector_id AS vectorId,kc.content AS content,kc.status AS chunkStatus,"
            + "kc.embedding_model AS embeddingModel,kc.embedding_version AS embeddingVersion,"
            + "kd.id AS joinedDocumentId,kd.title AS title,kd.category AS category,kd.source AS source,"
            + "kd.processing_status AS documentStatus,kd.document_version AS currentDocumentVersion "
            + "FROM knowledge_chunk kc LEFT JOIN knowledge_document kd ON kd.id=kc.document_id WHERE kc.vector_id IN (%s)";
    private final Connection connection;
    private final Set<String> allowed;
    final List<Map<String,Object>> audit = new ArrayList<>();
    final Map<String,String> identity;
    private boolean closed;

    IndependentReadOnlySql(Connection connection, Map<String,String> corpusSql) throws Exception {
        this.connection = connection;
        allowed = new HashSet<>(List.of(IDENTITY, PRIVILEGES, SCHEMA_PRIVILEGES));
        allowed.addAll(corpusSql.values());
        for (int size=1;size<=5;size++) {
            allowed.add(idsSql(HYDRATE,size)); allowed.add(idsSql(LOOKUP,size));
        }
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setReadOnly(true); // Connector/J sends SET SESSION TRANSACTION READ ONLY.
        connection.setAutoCommit(false);
        // The only non-SELECT SQL is this fixed transaction-control statement; never exposed to callers.
        try (var statement=connection.createStatement()) {
            statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
        }
        identity = query(IDENTITY).getFirst();
        validateIdentity(identity);
        RagE2ePolicy.require(connection.isReadOnly() && !connection.getAutoCommit(),"JDBC_READ_ONLY_REQUIRED");
    }
    static void validateIdentity(Map<String,String> row) {
        RagE2ePolicy.require("ai_interview_test".equals(row.get("db"))
                && "rag_e2e_writer@127.0.0.1".equals(row.get("account"))
                && RagE2ePolicy.SERVER_UUID.equals(row.get("uuid")),"MYSQL_IDENTITY_MISMATCH");
        RagE2ePolicy.require("1".equals(row.get("read_only")),"MYSQL_SERVER_READ_ONLY_REQUIRED");
        RagE2ePolicy.require("NONE".equals(row.get("roles")) && "".equals(row.get("mandatory")),"MYSQL_ROLES_REJECTED");
    }
    static String idsSql(String template,int count) {
        RagE2ePolicy.require(count>=1 && count<=5,"SQL_VECTOR_COUNT");
        return template.formatted(String.join(",",Collections.nCopies(count,"?")));
    }
    static String corpusSql(String table,List<String> columns) {
        RagE2ePolicy.require(RagE2ePolicy.CORPUS.contains(table) && !columns.isEmpty()
                && columns.stream().allMatch(c->c.matches("[a-z_]+")),"CORPUS_SQL_ALLOWLIST");
        return "SELECT "+columns.stream().map(c->"`"+c+"`").collect(Collectors.joining(","))
                +" FROM `"+table+"` ORDER BY id LIMIT 1001";
    }
    List<Map<String,String>> query(String sql,Object... parameters) throws Exception {
        RagE2ePolicy.require(allowed.contains(sql),"SQL_NOT_IN_FIXED_SELECT_ALLOWLIST");
        RagE2ePolicy.require(sql.startsWith("SELECT ") && !sql.contains(";"),"SELECT_ONLY");
        var record=new LinkedHashMap<String,Object>(); record.put("sql",sql);
        record.put("parameters",Arrays.asList(parameters)); audit.add(record);
        try (var statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10); statement.setMaxRows(1001);
            for (int i=0;i<parameters.length;i++) statement.setObject(i+1,parameters[i]);
            try (var result=statement.executeQuery()) {
                var rows=new ArrayList<Map<String,String>>(); var metadata=result.getMetaData();
                while (result.next()) {
                    RagE2ePolicy.require(rows.size()<1000,"SQL_ROW_LIMIT");
                    var row=new LinkedHashMap<String,String>();
                    for (int i=1;i<=metadata.getColumnCount();i++) row.put(metadata.getColumnLabel(i),result.getString(i));
                    rows.add(row);
                }
                record.put("rows",rows.size()); record.put("rowsSha256",RagE2ePolicy.rowHash(rows)); return rows;
            }
        }
    }
    public void close() throws SQLException {
        if(closed) return;
        closed=true;
        try { connection.rollback(); } finally { connection.close(); }
    }
}
