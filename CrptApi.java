package dangerwind;

/* -----------------------------------------------------------------------------------
 в документации ошибки!!!!!

 стр. 109 в JSON параметр "importRequest": true, то есть boolean
 а в "Параметры документа:" importRequest Тип: string

 и importRequest написан с использованием camelCase вероятно должен быть в snake_case

 на стр.44 в POST запросе сигнатура (подпись) передается в json
 но в спецификации на стр.108-109 нет сигнатуры (подписи) в json!!!
 потому сигнатуру(подпись) добавил в кастомный заголовок X-Signature

 на стр.110 reg_date описан как YYYY-MM-DDTHH:mm:ss
 но в json выведен "reg_date": "2020-01-23" без минут и секунд!
 и указан неоднозначно string(date-time)

production_date указан как string хотя в описании это "дата производства" и
вероятно должно быть data-time
------------------------------------------------------------------------------------- */

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {

        this.objectMapper.registerModule(new JavaTimeModule());

// задаем максимальное количество запросов
        this.semaphore = new Semaphore(requestLimit);

// сбрасываем семафор каждые сколько-то времени что указано в timeUnit
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            semaphore.drainPermits();
            semaphore.release(requestLimit);
        }, 0, timeUnit.toMillis(1), TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document docum, String signature, String token) {
// стр. 44   2.1.7. Единый метод создания документов  URL: /api/v3/lk/documents/create
// https://ismp.crpt.ru/api/v3/lk/documents/create

        try {
            semaphore.acquire();

            var json = objectMapper.writeValueAsString(docum);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
// подпись signature передаётся через заголовок X-Signature
// т.к. в описании на json нет такого поля
                    .header("X-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new RuntimeException("Ошибка при создании документа.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при выполнении запроса.", e);
        }
    }

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Document {
    // стр. 108-109  2.2.4.1  Ввод в оборот товара, произведенного на территории РФ

        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        private boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("production_type")
        private String productionType;

        private List<Products> products;

        @JsonProperty("reg_date")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        @Getter
        @Setter
        public static class Description {
            @JsonProperty("participant_inn")
            private String participantInn;
        }

        @Getter
        @Setter
        public static class Products {
            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonProperty("certificate_document_date")
            private String certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            private String productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }
}
