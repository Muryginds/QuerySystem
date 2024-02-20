package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final String APPLICATION_JSON = "application/json";
    private final int requestLimit;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Queue<Long> queryTimeHolder;
    private final Long awaitingIntervalMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        httpClient = new OkHttpClient();
        objectMapper = new ObjectMapper();
        queryTimeHolder = new ArrayDeque<>(requestLimit);
        awaitingIntervalMillis = timeUnit.toMillis(1);
    }

    public void createDocument(DocumentDto document, String signature) {
        Response response = null;
        try {
            var httpRequest = createRequest(document);

            synchronized (this) {
                while (queryTimeHolder.size() >= requestLimit) {
                    var timeToWait = getTimeToWait();

                    if (timeToWait > 0) {
                        this.wait(timeToWait);
                    }

                    removeOldQueryTimes();
                }

                queryTimeHolder.offer(System.currentTimeMillis());

                response = httpClient.newCall(httpRequest).execute();
            }

            if (response.isSuccessful()) {
                System.out.println("Document created successfully.");
            } else {
                System.out.println("Failed to create document. Status code: " + response.code());
                if (response.body() != null) {
                    System.out.println("Response body: " + response.body().string());
                }
            }

        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @NotNull
    private Request createRequest(DocumentDto document) throws JsonProcessingException {
        var requestBody = RequestBody.create(
                objectMapper.writeValueAsString(document),
                MediaType.parse(APPLICATION_JSON)
        );
        return new Request.Builder()
                .url(URL)
                .header("Content-Type", APPLICATION_JSON)
                .post(requestBody)
                .build();
    }

    private long getTimeToWait() {
        var oldestQueryTime = Optional.ofNullable(queryTimeHolder.peek()).orElse(0L);
        return awaitingIntervalMillis - (System.currentTimeMillis() - oldestQueryTime);
    }

    private void removeOldQueryTimes() {
        var currentTime = System.currentTimeMillis();
        var oldestQueryTime = currentTime - awaitingIntervalMillis;

        while (!queryTimeHolder.isEmpty() && queryTimeHolder.peek() < oldestQueryTime) {
            queryTimeHolder.poll();
        }
    }

    public class DocumentDto {
        public DescriptionDto description;
        @JsonProperty("doc_id")
        public String docId;
        @JsonProperty("doc_status")
        public String docStatus;
        @JsonProperty("doc_type")
        public String docType;
        public boolean importRequest;
        @JsonProperty("owner_inn")
        public String ownerInn;
        @JsonProperty("participant_inn")
        public String participantInn;
        @JsonProperty("producer_inn")
        public String producerInn;
        @JsonProperty("production_date")
        public String productionDate;
        @JsonProperty("production_type")
        public String productionType;
        public List<ProductDto> products;
        @JsonProperty("reg_date")
        public String regDate;
        @JsonProperty("reg_number")
        public String regNumber;
    }

    public class DescriptionDto {
        public String participantInn;
    }

    public class ProductDto {
        @JsonProperty("certificate_document")
        public String certificateDocument;
        @JsonProperty("certificate_document_date")
        public String certificateDocumentDate;
        @JsonProperty("certificate_document_number")
        public String certificateDocumentNumber;
        @JsonProperty("owner_inn")
        public String ownerInn;
        @JsonProperty("producer_inn")
        public String producerInn;
        @JsonProperty("production_date")
        public String productionDate;
        @JsonProperty("tnved_code")
        public String tnvedCode;
        @JsonProperty("uit_code")
        public String uitCode;
        @JsonProperty("uitu_code")
        public String uituCode;
    }
}