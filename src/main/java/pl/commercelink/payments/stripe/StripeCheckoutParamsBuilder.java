package pl.commercelink.payments.stripe;

import pl.commercelink.payments.api.PaymentShippingItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class StripeCheckoutParamsBuilder {

    private final Map<String, Object> params;

    private StripeCheckoutParamsBuilder(Map<String, Object> params) {
        this.params = params;
    }

    static StripeCheckoutParamsBuilder builder(String currency, String successUrl, String cancelUrl) {
        Map<String, Object> params = new HashMap<>();
        params.put("customer_creation", "always");
        params.put("success_url", successUrl);
        params.put("cancel_url", cancelUrl);
        params.put("mode", "payment");
        params.put("currency", currency);
        params.put("locale", "pl");
        params.put("allow_promotion_codes", true);

        return new StripeCheckoutParamsBuilder(params);
    }

    StripeCheckoutParamsBuilder withLineItems(List<Object> lineItems) {
        params.put("line_items", lineItems);
        return this;
    }

    StripeCheckoutParamsBuilder withConsentCollection() {
        params.put("consent_collection", mapOf("terms_of_service", "required"));
        return this;
    }

    StripeCheckoutParamsBuilder withShippingDetails(PaymentShippingItem shippingItem, String currency) {
        Map<String, Object> fixedAmount = new HashMap<>();
        fixedAmount.put("amount", shippingItem.amount());
        fixedAmount.put("currency", currency);

        Map<String, Object> shippingRateData = new HashMap<>();
        shippingRateData.put("display_name", shippingItem.name());
        shippingRateData.put("type", "fixed_amount");
        shippingRateData.put("fixed_amount", fixedAmount);
        shippingRateData.put("delivery_estimate", Map.of(
                "minimum", Map.of("value", shippingItem.minDeliveryDays(), "unit", "business_day"),
                "maximum", Map.of("value", shippingItem.maxDeliveryDays(), "unit", "business_day")
        ));

        params.put("shipping_options", List.of(
                mapOf("shipping_rate_data", shippingRateData)
        ));

        return this;
    }

    StripeCheckoutParamsBuilder withPaymentIntentParams(String basketId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("Basket Id", basketId);

        params.put("payment_intent_data", mapOf("metadata", metadata));

        return this;
    }

    Map<String, Object> build() {
        return this.params;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        return params;
    }
}
