package ru.sshibko;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final Semaphore semaphore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(CrptApi.class);
    private final ScheduledExecutorService executorService;
    private final TimeUnit timeUnit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        this.executorService = Executors.newScheduledThreadPool(1);
        this.timeUnit = timeUnit;

        // Init Thread and Semaphore in Class Constructor
        Runnable releaseTask = () -> {
            try {
                Thread.sleep(timeUnit.toMillis((1)));
                semaphore.release(requestLimit - semaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };

        Thread releaseThread = new Thread(releaseTask);
        releaseThread.setDaemon(true);
        releaseThread.start();

    }

    /**
     * Sending document in request body and signature in request header in encrypted form,
     * Expecting a response (realized - should work API for specified URL)
     */
    public void createDocument(Document document, String signature, String url) {
        try {
            semaphore.acquire();
            executorService.scheduleAtFixedRate(semaphore::release, 1, 1, timeUnit);

            String requestBody = objectMapper.writeValueAsString(document);

            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Signature", encodeBase64(signature));

            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpResponse response = (HttpResponse) httpClient.execute(httpPost, new CustomHttpClientResponseHandler());

            int statusCode = response.getCode();
            logger.info("HTTP Status Code: " + statusCode);

            httpClient.close();
        } catch (InterruptedException | IOException e) {
            logger.error("Error");
            e.printStackTrace();
        }
    }

    /**
     * Basic Encryptor Method for signature
     */
    public String encodeBase64(String data) {
        return new String(Base64.getEncoder().encode(data.getBytes()));
    }

    /**
     * Custom HttpResponse Exception Handler if we expect a signed document in response
     * (should work API for specified URL)
     */
    public static class CustomHttpClientResponseHandler implements HttpClientResponseHandler<Document> {

        @Override
        public Document handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int status = response.getCode();
            if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                final JsonFactory jsonFactory = new JsonFactory();

                try (HttpEntity httpEntity = response.getEntity();
                     InputStream inputStream = httpEntity.getContent()) {

                    JsonParser jsonParser = jsonFactory.createParser(inputStream);
                    jsonParser.setCodec(new ObjectMapper());
                    Document document = jsonParser.readValueAs(Document.class);
                    return document;
                }
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        }
    }

    /**
     * Custom method for class test
     */
    public void runClassCrptApi(Document document, String signature, String url, int requestNumber) {
        for (int i = 0; i < requestNumber; i++) {
            createDocument(document, signature, url);
        }
        executorService.shutdown();
    }

    /**
     * Inner Classes for Document Entity
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Description {
        private String participantInn;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    public enum doc_type {
        LP_INTRODUCE_GOODS("LP_INTRODUCE_GOODS");

        private final String text;

        doc_type(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private doc_type doc_type;
        private boolean importRequest = true;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }
}
