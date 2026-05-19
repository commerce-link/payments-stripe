package pl.commercelink.payments.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import pl.commercelink.payments.api.PaymentLineItem;
import pl.commercelink.payments.api.PaymentLink;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class StripePaymentProvider implements PaymentProvider {

    private final RequestOptions requestOptions;

    StripePaymentProvider(String secretKey) {
        this.requestOptions = RequestOptions.builder()
                .setApiKey(secretKey)
                .build();
    }

    @Override
    public PaymentLink createPaymentLink(PaymentRequest request) {
        StripeCheckoutParamsBuilder builder = StripeCheckoutParamsBuilder.builder(
                        request.currency(), request.successUrl(), request.cancelUrl())
                .withLineItems(toStripeLineItems(request.lineItems(), request.currency()))
                .withPaymentIntentParams(request.orderId())
                .withConsentCollection();

        if (request.shippingItem() != null) {
            builder.withShippingDetails(request.shippingItem(), request.currency());
        }

        try {
            String url = Session.create(builder.build(), requestOptions).getUrl();
            return new PaymentLink(url, "GET", null);
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
}
