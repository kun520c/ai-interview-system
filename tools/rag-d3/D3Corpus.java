import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class D3Corpus {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = Path.of("docs/rag/d3-b");
    static final Path MAPPING = ROOT.resolve("D3A_MAPPING_EXPORT.json");
    static final Path CHUNKER = Path.of("src/main/java/com/kun/aiinterview/knowledge/chunk/KnowledgeTextChunker.java");
    static final Path E01 = Path.of("docs/rag/source-documents/e01_java21_interface_vs_abstract_class.md");
    static final String E01_SHA = "4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0";

    record Chunk(int index, int utf16Length, int utf8Bytes, String sha256) {}
    record Document(String id, String title, KnowledgeCategory category, String file, String fileSha256,
                    String normalizedSha256, int documentVersion, boolean reuseE01, List<Chunk> chunks) {}
    record Manifest(String version, String chunkerSha256, String mappingSha256, String javaVersion,
                    List<Document> documents) {}

    private D3Corpus() {}

    public static void main(String[] args) throws Exception {
        require(args.length == 1 || args.length == 2 && "--draft".equals(args[1]), "ARGUMENTS");
        boolean complete = args.length == 1;
        Manifest manifest = build(complete);
        writeNew(Path.of(args[0]), manifest);
        System.out.printf("documents=%d chunks=%d completeCatalog=%s%n", manifest.documents().size(),
                manifest.documents().stream().mapToInt(doc -> doc.chunks().size()).sum(), complete);
    }

    static Manifest build(boolean complete) throws Exception {
        JsonNode mapping = JSON.readTree(MAPPING.toFile());
        List<Document> documents = new ArrayList<>();
        Set<String> documentHashes = new HashSet<>();
        Set<String> chunkHashes = new HashSet<>();
        Set<Path> expectedFiles = new HashSet<>();
        for (JsonNode row : mapping.required("catalog")) {
            String id = row.required("文档 ID").asText();
            boolean e01 = "J01".equals(id);
            Path file = e01 ? E01 : ROOT.resolve("corpus/" + id.toLowerCase(java.util.Locale.ROOT) + ".md");
            expectedFiles.add(file);
            if (!complete && !Files.exists(file)) {
                continue;
            }
            String original = Files.readString(file, StandardCharsets.UTF_8);
            require(Files.size(file) <= 5 * 1024 * 1024, "DOCUMENT_SIZE_" + id);
            String normalized = normalize(original);
            String sourceHash = sha(Files.readAllBytes(file));
            String contentHash = sha(normalized);
            require(!e01 || E01_SHA.equals(sourceHash) && E01_SHA.equals(contentHash), "E01_BYTES");
            require(documentHashes.add(contentHash), "DUPLICATE_DOCUMENT_" + id);
            List<Chunk> chunks = new KnowledgeTextChunker().split(normalized).stream().map(draft -> {
                String text = draft.content();
                String digest = sha(text);
                require(chunkHashes.add(digest), "DUPLICATE_CHUNK_" + id);
                require(draft.characterCount() == text.length() && !text.isBlank() && text.length() <= 1200
                        && StandardCharsets.UTF_8.newEncoder().canEncode(text), "CHUNK_TEXT_" + id);
                require(!text.contains("===== RAG EVIDENCE ====="), "CHUNK_RESERVED_MARKER_" + id);
                return new Chunk(draft.chunkIndex(), text.length(), text.getBytes(StandardCharsets.UTF_8).length, digest);
            }).toList();
            String title = e01 ? "Java 21 接口与抽象类：契约、状态与选择" : row.required("拟建文档主题").asText();
            documents.add(new Document(id, title, category(id), file.toString(), sourceHash,
                    contentHash, 1, e01, chunks));
        }
        try (var files = Files.list(ROOT.resolve("corpus"))) {
            require(files.filter(path -> path.toString().endsWith(".md")).allMatch(expectedFiles::contains),
                    "UNKNOWN_CORPUS_FILE");
        }
        require(!complete || documents.size() == 51, "DOCUMENT_COUNT");
        return new Manifest(complete ? "D3-BCD-20261114-v1" : "D3-BCD-draft", sha(Files.readAllBytes(CHUNKER)),
                sha(Files.readAllBytes(MAPPING)), Runtime.version().toString(), List.copyOf(documents));
    }

    static KnowledgeCategory category(String id) {
        int number = Integer.parseInt(id.substring(1));
        return switch (id.charAt(0)) {
            case 'J' -> number <= 8 ? KnowledgeCategory.JAVA_BASIC : number <= 15
                    ? KnowledgeCategory.JAVA_COLLECTION : number <= 19 ? KnowledgeCategory.JVM
                    : KnowledgeCategory.JAVA_CONCURRENCY;
            case 'S' -> number <= 8 ? KnowledgeCategory.SPRING : KnowledgeCategory.PROJECT_SCENARIO;
            case 'D' -> number <= 8 ? KnowledgeCategory.MYSQL : number <= 14 ? KnowledgeCategory.REDIS
                    : KnowledgeCategory.NETWORK;
            default -> throw new IllegalArgumentException("UNKNOWN_DOCUMENT_CATEGORY");
        };
    }

    static String normalize(String text) {
        String noBom = text.startsWith("\uFEFF") ? text.substring(1) : text;
        return noBom.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String sha(String text) {
        return sha(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    static void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }

    static void writeNew(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardOpenOption.CREATE_NEW);
    }
}
