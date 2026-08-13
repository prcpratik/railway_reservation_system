package com.eureka.railway.controller;

import com.eureka.railway.dto.Dtos.PaymentConfig;
import com.eureka.railway.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // the key id is public (it appears in the checkout popup anyway); the secret never leaves the server
    @GetMapping("/config")
    public PaymentConfig config() {
        return new PaymentConfig(paymentService.isEnabled(), paymentService.getKeyId());
    }
}
