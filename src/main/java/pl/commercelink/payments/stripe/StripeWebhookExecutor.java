package pl.commercelink.payments.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.ChargeRetrieveParams;
import pl.commercelink.payments.api.PaymentWebhookResult;
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookExecutor;

class StripeWebhookExecutor implements WebhookExecutor<String, PaymentWebhookResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public PaymentWebhookResult execute(String payload, WebhookContext ctx) {
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
