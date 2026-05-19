package pl.commercelink.payments.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.ChargeRetrieveParams;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.payments.api.PaymentWebhookResult;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.WebhookContext;

import java.util.List;
import java.util.Map;

import static pl.commercelink.provider.api.ProviderField.FieldType.PASSWORD;

public class StripePaymentProviderDescriptor implements PaymentProviderDescriptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public String displayName() {
        return "Stripe";
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of(
                new ProviderField("apiKey", "API Key", PASSWORD, true, ""),
                new ProviderField("signingSecret", "Signing Secret", PASSWORD, true, "")
        );
    }

    @Override
    public PaymentProvider create(Map<String, String> configuration) {
        return new StripePaymentProvider(configuration.get("apiKey"));
    }

    @Override
    public List<EventBinding<?>> bindings() {
        return List.of(new WebhookBinding<>("stripe", String.class, this::handleWebhook));
    }

    private PaymentWebhookResult handleWebhook(String payload, WebhookContext ctx) {
        String signingSecret = ctx.providerConfig().get("signingSecret");
        if (signingSecret == null || signingSecret.isEmpty()) {
            throw new RuntimeException("Signing secret not configured");
        }

        String stripeSignature = ctx.header("Stripe-Signature");
        try {
            Webhook.constructEvent(payload, stripeSignature, signingSecret);
        } catch (SignatureVerificationException e) {
            throw new RuntimeException("Invalid signature: " + e.getMessage());
        }

        try {
            StripeWebhookPayload parsed = OBJECT_MAPPER.readValue(payload, StripeWebhookPayload.class);
            if (!"charge.succeeded".equals(parsed.getType())) {
                return new PaymentWebhookResult(null, null, 0, false);
            }

            StripeWebhookPayload.ChargeObject charge = parsed.getData().getObject();
            if (!charge.isPaid() || !"succeeded".equals(charge.getStatus())) {
                return new PaymentWebhookResult(null, null, 0, false);
            }

            StripeWebhookPayload.Metadata metadata = charge.getMetadata();
            if (metadata.getBasketId() == null) {
                System.out.println("Basket ID is missing in metadata for charge: " + charge.getId() + " ignoring.");
                return new PaymentWebhookResult(null, null, 0, false);
            }

            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(ctx.providerConfig().get("apiKey"))
                    .build();
            double fee = fetchFee(charge.getId(), requestOptions);
            return new PaymentWebhookResult(metadata.getBasketId(), charge.getPaymentIntent(), fee, true);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to fetch fee: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Error processing webhook: " + e.getMessage());
        }
    }

    private static double fetchFee(String chargeId, RequestOptions requestOptions) throws StripeException {
        ChargeRetrieveParams params = ChargeRetrieveParams.builder().addExpand("balance_transaction").build();
        Charge charge = Charge.retrieve(chargeId, params, requestOptions);
        return charge.getBalanceTransactionObject().getFee() / 100.0;
    }
}
