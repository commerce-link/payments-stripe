package pl.commercelink.payments.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class StripeWebhookPayload {

    @JsonProperty("type")
    private String type;

    @JsonProperty("data")
    private Data data;

    String getType() {
        return type;
    }

    Data getData() {
        return data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data {

        @JsonProperty("object")
        private ChargeObject object;

        ChargeObject getObject() {
            return object;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChargeObject {

        @JsonProperty("id")
        private String id;

        @JsonProperty("paid")
        private boolean paid;

        @JsonProperty("status")
        private String status;

        @JsonProperty("metadata")
        private Metadata metadata;

        @JsonProperty("payment_intent")
        private String paymentIntent;

        String getId() {
            return id;
        }

        boolean isPaid() {
            return paid;
        }

        String getPaymentIntent() {
            return paymentIntent;
        }

        String getStatus() {
            return status;
        }

        Metadata getMetadata() {
            return metadata;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Metadata {

        @JsonProperty("Basket Id")
        private String basketId;

        String getBasketId() {
            return basketId;
        }
    }
}
