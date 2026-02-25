# Stripe Payments

[Stripe](https://stripe.com) implementation of the [payments-api](https://github.com/commerce-link/payments-api) provider interface.

Supports creating Stripe Checkout payment links and processing charge webhooks with signature verification and fee extraction.

## Provider Discovery

This library registers itself for `ServiceLoader` discovery. Add it to your classpath and the provider will be available automatically via `PaymentProviderDescriptor` SPI. See the [provider-api README](https://github.com/commerce-link/provider-api) for details.

## Configuration Fields

| Key             | Label          | Type     | Required |
|-----------------|----------------|----------|----------|
| `apiKey`        | API Key        | PASSWORD | yes      |
| `signingSecret` | Signing Secret | PASSWORD | yes      |
