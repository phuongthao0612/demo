package com.example.demo.controller;

import com.example.demo.model.Transaction;
import com.example.demo.repository.TransactionRepository;
import com.example.demo.service.PaypalService;
import com.example.demo.service.VnpayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "http://localhost:3000")
public class TransactionController {

    @Autowired
    private VnpayService vnPayService;

    @Autowired
    private PaypalService payPalService;

    @Autowired
    private TransactionRepository transactionRepository;

    // Endpoint tạo giao dịch, trả về URL chuyển hướng cho thanh toán
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createTransaction(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Double amount = Double.valueOf(request.get("amount").toString());
        String paymentMethod = request.get("paymentMethod").toString();

        String redirectUrl = "";
        if ("VNPAY".equalsIgnoreCase(paymentMethod)) {
            redirectUrl = vnPayService.createPaymentUrl(userId, amount);
        } else if ("PAYPAL".equalsIgnoreCase(paymentMethod)) {
            redirectUrl = payPalService.createPayment(userId, amount);
        }

        return ResponseEntity.ok(Collections.singletonMap("redirectUrl", redirectUrl));
    }

    // Endpoint callback của VNPAY
    @GetMapping("/vnpay-return")
    public ResponseEntity<String> returnPayment(
            @RequestParam("vnp_ResponseCode") String responseCode,
            @RequestParam("vnp_TxnRef") String vnp_TxnRef) {
        return vnPayService.handleVnPayReturn(responseCode, vnp_TxnRef);
    }

    // Endpoint callback của PayPal
    @GetMapping("/paypal-return")
    public ResponseEntity<String> paypalReturn(
            @RequestParam("transactionId") String transactionId,
            @RequestParam("paymentId") String paymentId,
            @RequestParam("PayerID") String payerId) {
        String result = payPalService.handlePayPalReturn(transactionId, paymentId, payerId);
        return ResponseEntity.ok(result);
    }

    // Endpoint lấy danh sách giao dịch của người dùng
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Transaction>> getUserTransactions(@PathVariable Long userId) {
        List<Transaction> transactions = transactionRepository.findAll()
                .stream()
                .filter(t -> t.getUserId().equals(userId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(transactions);
    }
}
