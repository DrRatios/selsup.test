package com.aleksgolds;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final HttpClient httpClient;
    private final ReentrantLock lock;
    private final Deque<Instant> requestTimes;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.lock = new ReentrantLock();
        this.requestTimes = new ArrayDeque<>(requestLimit);
    }

    public void createDocument(Document document, String signature) {
        try {
            waitForRequestLimit();

            String requestBody = buildRequestBody(document, signature);
            HttpRequest request = buildRequest(requestBody);

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            handleResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread was interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create document", e);
        }
    }

    private void waitForRequestLimit() throws InterruptedException {
        lock.lock();
        try {
            Instant now = Instant.now();
            Instant threshold = now.minusMillis(timeUnit.toMillis(1));

            while (!requestTimes.isEmpty() && requestTimes.peek().isBefore(threshold)) {
                requestTimes.poll();
            }

            if (requestTimes.size() >= requestLimit) {
                Instant oldestRequest = requestTimes.peek();
                long waitTime = Duration.between(threshold, oldestRequest).toMillis();
                Thread.sleep(waitTime);

                requestTimes.poll();
                waitForRequestLimit();
            } else {
                requestTimes.add(Instant.now());
            }
        } finally {
            lock.unlock();
        }
    }

    private String buildRequestBody(Document document, String signature) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"product_document\":").append(toJson(document)).append(",");
        sb.append("\"document_format\":\"MANUAL\",");
        sb.append("\"type\":\"LP_INTRODUCE_GOODS\",");
        sb.append("\"signature\":\"").append(escapeJson(signature)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String toJson(Document document) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "description", document.description);
        appendField(sb, "participant_inn", document.participant_inn);
        appendField(sb, "doc_id", document.doc_id);
        appendField(sb, "doc_status", document.doc_status);
        appendField(sb, "doc_type", document.doc_type);
        sb.append("\"importRequest\":").append(document.importRequest).append(",");
        appendField(sb, "owner_inn", document.owner_inn);
        appendField(sb, "producer_inn", document.producer_inn);
        appendField(sb, "production_date", document.production_date);
        appendField(sb, "production_type", document.production_type);

        if (document.products != null && document.products.length > 0) {
            sb.append("\"products\":[");
            for (int i = 0; i < document.products.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(document.products[i]));
            }
            sb.append("],");
        }

        appendField(sb, "reg_date", document.reg_date);
        appendField(sb, "reg_number", document.reg_number);

        if (sb.charAt(sb.length()-1) == ',') {
            sb.setLength(sb.length()-1);
        }

        sb.append("}");
        return sb.toString();
    }

    private String toJson(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "certificate_document", product.certificate_document);
        appendField(sb, "certificate_document_date", product.certificate_document_date);
        appendField(sb, "certificate_document_number", product.certificate_document_number);
        appendField(sb, "owner_inn", product.owner_inn);
        appendField(sb, "producer_inn", product.producer_inn);
        appendField(sb, "production_date", product.production_date);
        appendField(sb, "tnved_code", product.tnved_code);
        appendField(sb, "uit_code", product.uit_code);
        appendField(sb, "uitu_code", product.uitu_code);

        if (sb.charAt(sb.length()-1) == ',') {
            sb.setLength(sb.length()-1);
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String name, String value) {
        if (value != null) {
            sb.append("\"").append(name).append("\":\"")
                    .append(escapeJson(value)).append("\",");
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private HttpRequest buildRequest(String requestBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("Document created successfully: " + response.body());
        } else {
            throw new RuntimeException("API request failed with status code: " +
                    response.statusCode() + ", body: " + response.body());
        }
    }

    public static class Document {
        public String description;
        public String participant_inn;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;
    }

    public static class Product {
        public String certificate_document;
        public String certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public String production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;
    }
}