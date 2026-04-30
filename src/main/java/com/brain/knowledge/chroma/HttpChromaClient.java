package com.brain.knowledge.chroma;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpChromaClient implements ChromaClient {
    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    private final ChromaConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final McpJsonMapper mapper = McpJsonDefaults.getMapper();
    private final Map<ChromaCollection, String> collectionIds = new LinkedHashMap<>();

    public HttpChromaClient(ChromaConfig config) {
        this.config = config;
    }

    @Override
    public List<String> upsert(List<ChromaDocument> documents) throws IOException {
        Map<ChromaCollection, List<ChromaDocument>> grouped = new LinkedHashMap<>();
        for (ChromaDocument document : documents) {
            grouped.computeIfAbsent(document.collection(), ignored -> new ArrayList<>()).add(document);
        }

        List<String> writtenIds = new ArrayList<>();
        for (Map.Entry<ChromaCollection, List<ChromaDocument>> entry : grouped.entrySet()) {
            String collectionId = ensureCollection(entry.getKey());
            writeDocuments(collectionId, entry.getValue());
            entry.getValue().stream().map(ChromaDocument::id).forEach(writtenIds::add);
        }
        return List.copyOf(writtenIds);
    }

    @Override
    public List<ChromaDocument> query(ChromaQuery query) throws IOException {
        throw new IOException("query is not implemented yet");
    }

    private String ensureCollection(ChromaCollection collection) throws IOException {
        String existing = collectionIds.get(collection);
        if (existing != null) {
            return existing;
        }

        String expectedName = collectionName(collection);
        HttpResponse<String> response = send(request(collectionsUri(), "GET", null));
        List<?> collections = mapper.readValue(response.body(), List.class);
        for (Object item : collections) {
            if (item instanceof Map<?, ?> map) {
                Object name = map.get("name");
                Object id = map.get("id");
                if (expectedName.equals(name) && id != null) {
                    String resolved = id.toString();
                    collectionIds.put(collection, resolved);
                    return resolved;
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", expectedName);
        payload.put("metadata", Map.of("source", "project_brain"));
        HttpResponse<String> created = send(request(collectionsUri(), "POST", mapper.writeValueAsString(payload)));
        Map<?, ?> body = mapper.readValue(created.body(), Map.class);
        Object id = body.get("id");
        if (id == null) {
            throw new IOException("create collection response missing id for " + expectedName);
        }
        String resolved = id.toString();
        collectionIds.put(collection, resolved);
        return resolved;
    }

    private void writeDocuments(String collectionId, List<ChromaDocument> documents) throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        List<List<Double>> embeddings = new ArrayList<>();
        for (ChromaDocument document : documents) {
            ids.add(document.id());
            texts.add(document.content());
            metadatas.add(metadataMap(document.metadata()));
            embeddings.add(document.embedding());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ids", ids);
        payload.put("documents", texts);
        payload.put("metadatas", metadatas);
        payload.put("embeddings", embeddings);
        send(request(collectionUpsertUri(collectionId), "POST", mapper.writeValueAsString(payload)));
    }

    private Map<String, Object> metadataMap(ChromaMetadata metadata) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("project_path", metadata.projectPath());
        map.put("project_name", metadata.projectName());
        map.put("module", metadata.module());
        map.put("stage", metadata.stage());
        map.put("task_session_id", metadata.taskSessionId());
        map.put("source_file", metadata.sourceFile());
        map.put("backup_file", metadata.backupFile());
        map.put("content_hash", metadata.contentHash());
        map.put("stable", metadata.stable());
        map.put("compacted", metadata.compacted());
        map.put("created_at", metadata.createdAt());
        return map;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Chroma request failed: " + response.statusCode() + " " + response.body());
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Chroma request interrupted", exception);
        }
    }

    private HttpRequest request(URI uri, String method, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("Content-Type", "application/json");
        if ("GET".equals(method)) {
            return builder.GET().build();
        }
        return builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build();
    }

    private URI collectionsUri() {
        return URI.create(apiBase() + "/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections");
    }

    private URI collectionUpsertUri(String collectionId) {
        return URI.create(collectionsUri() + "/" + collectionId + "/upsert");
    }

    private String collectionName(ChromaCollection collection) {
        return config.namespaceOrDefault() + "__" + collection.collectionName();
    }

    private String apiBase() {
        String url = trimTrailingSlash(config.url());
        if (url.endsWith("/api/v2")) {
            return url;
        }
        return url + "/api/v2";
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
