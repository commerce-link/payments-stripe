package pl.commercelink.payments.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.ChargeRetrieveParams;
import pl.commercelink.payments.api.PaymentLineItem;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentRequest;
import pl.commercelink.payments.api.PaymentWebhookRequest;
import pl.commercelink.payments.api.PaymentWebhookResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class StripePaymentProvider implements PaymentProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RequestOptions requestOptions;
    private final String signingSecret;

    StripePaymentProvider(String secretKey, String signingSecret) {
        this.requestOptions = RequestOptions.builder()
                .setApiKey(secretKey)
                .build();
        this.signingSecret = signingSecret;
    }

    @Override
    public String createPaymentLink(PaymentRequest request) {
        StripeCheckoutParamsBuilder builder = StripeCheckoutParamsBuilder.builder(
                        request.currency(), request.successUrl(), request.cancelUrl())
                .withLineItems(toStripeLineItems(request.lineItems(), request.currency()))
                .withPaymentIntentParams(request.orderId())
                .withConsentCollection();

        if (request.shippingItem() != null) {
            builder.withShippingDetails(request.shippingItem(), request.currency());
        }

        try {
            return Session.create(builder.build(), requestOptions).getUrl();
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Object> toStripeLineItems(List<PaymentLineItem> lineItems, String currency) {
        return lineItems.stream().map(item -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("name", item.name());
            if (item.description() != null) {
                productData.put("description", item.description());
            }

            Map<String, Object> priceData = new HashMap<>();
            priceData.put("unit_amount", item.amount());
            priceData.put("currency", currency);
            priceData.put("product_data", productData);

            Map<String, Object> lineItemMap = new HashMap<>();
            lineItemMap.put("price_data", priceData);
            lineItemMap.put("quantity", item.quantity());
            return (Object) lineItemMap;
        }).collect(Collectors.toList());
    }

    @Override
    public PaymentWebhookResult processWebhook(PaymentWebhookRequest request) {
        if (signingSecret == null || signingSecret.isEmpty()) {
            throw new RuntimeException("Signing secret not configured");
        }

        String stripeSignature = request.getHeader("Stripe-Signature");
        try {
            Webhook.constructEvent(request.payload(), stripeSignature, signingSecret);
        } catch (SignatureVerificationException e) {
            throw new RuntimeException("Invalid signature: " + e.getMessage());
        }

        try {
            StripeWebhookPayload payload = OBJECT_MAPPER.readValue(request.payload(), StripeWebhookPayload.class);
            if (!"charge.succeeded".equals(payload.getType())) {
                return new PaymentWebhookResult(null, null, 0, false);
            }

            StripeWebhookPayload.ChargeObject charge = payload.getData().getObject();
            if (!charge.isPaid() || !"succeeded".equals(charge.getStatus())) {
                return new PaymentWebhookResult(null, null, 0, false);
            }

            StripeWebhookPayload.Metadata metadata = charge.getMetadata();
            if (metadata.getBasketId() == null) {
                System.out.println("Basket ID is missing in metadata for charge: " + charge.getId() + " ignoring.");
                return new PaymentWebhookResult(null, null, 0, false);
            }

            double fee = fetchFee(charge.getId());
            return new PaymentWebhookResult(metadata.getBasketId(), charge.getPaymentIntent(), fee, true);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to fetch fee: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Error processing webhook: " + e.getMessage());
        }
    }

    private double fetchFee(String chargeId) throws StripeException {
        ChargeRetrieveParams params = ChargeRetrieveParams.builder().addExpand("balance_transaction").build();
        Charge charge = Charge.retrieve(chargeId, params, requestOptions);

        return charge.getBalanceTransactionObject().getFee() / 100.0;
    }
}
