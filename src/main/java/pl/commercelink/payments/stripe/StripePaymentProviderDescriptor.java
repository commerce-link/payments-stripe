package pl.commercelink.payments.stripe;

import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;

import static pl.commercelink.provider.api.ProviderField.FieldType.PASSWORD;

public class StripePaymentProviderDescriptor implements PaymentProviderDescriptor {

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
        return new StripePaymentProvider(configuration.get("apiKey"), configuration.get("signingSecret"));
    }
}
