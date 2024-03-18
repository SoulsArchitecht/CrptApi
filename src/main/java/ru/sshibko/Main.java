package ru.sshibko;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    private final static String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final static String dataPath = "src/main/resources/data.json";
    private final static String signature = "signature";
    private final static int requestNumber = 10;

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);
        ObjectMapper objectMapper = new ObjectMapper();
        CrptApi.Document document = null;

        try {
            document = objectMapper.readValue(new FileInputStream(dataPath), CrptApi.Document.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        api.runClassCrptApi(document, signature, URL, requestNumber);
    }
}