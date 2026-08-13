package com.eureka.railway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

// talks to the Razorpay REST API using RestTemplate (no SDK needed)
@Service
public class PaymentService {

    private final boolean enabled;
    private final String keyId;
    private final String keySecret;
    private final RestTemplate restTemplate = new RestTemplate();

    public PaymentService(@Value("${razorpay.enabled}") boolean enabled,
                          @Value("${razorpay.key-id}") String keyId,
                          @Value("${razorpay.key-secret}") String keySecret) {
        this.enabled = enabled;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getKeyId() {
        return keyId;
    }

    // creates a Razorpay order; the returned order id is what the checkout popup needs
    public String createOrder(double amountRupees, String receipt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(keyId, keySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "amount", Math.round(amountRupees * 100), // Razorpay expects paise
                "currency", "INR",
                "receipt", receipt);

        Map<?, ?> response = restTemplate.postForObject(
                "https://api.razorpay.com/v1/orders", new HttpEntity<>(body, headers), Map.class);
        return (String) response.get("id");
    }

    // refund (part of) a captured payment. amountRupees is refunded to the
    // customer's original payment method. Returns the Razorpay refund id.
    public String refund(String paymentId, double amountRupees) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(keyId, keySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("amount", Math.round(amountRupees * 100)); // paise

        try {
            Map<?, ?> response = restTemplate.postForObject(
                    "https://api.razorpay.com/v1/payments/" + paymentId + "/refund",
                    new HttpEntity<>(body, headers), Map.class);
            return response == null ? null : (String) response.get("id");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Refund could not be processed. Please try again.");
        }
    }

    // Razorpay signs "orderId|paymentId" with the key secret (HMAC SHA-256).
    // We recompute it server-side - the frontend can never fake a successful payment.
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
