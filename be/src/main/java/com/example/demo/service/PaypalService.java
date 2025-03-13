package com.example.demo.service;

import com.example.demo.model.Transaction;
import com.example.demo.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PaypalService {

    @Value("${paypal.clientId}")
    private String clientId;

    @Value("${paypal.clientSecret}")
    private String clientSecret;

    @Value("${paypal.mode}")
    private String mode;

    @Value("${paypal.returnUrl}")
    private String returnUrl;

    @Value("${paypal.cancelUrl}")
    private String cancelUrl;

    private final TransactionRepository transactionRepository;

    public PaypalService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // Lấy access token từ PayPal
    private String getAccessToken() {
        RestTemplate restTemplate = new RestTemplate();
        String authUrl = "https://api.sandbox.paypal.com/v1/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Tạo Basic Auth header từ clientId:clientSecret
        String auth = clientId + ":" + clientSecret;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(authUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> map = response.getBody();
                return map.get("access_token").toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Tạo Payment thông qua PayPal API và trả về URL chuyển hướng cho người dùng
    public String createPayment(Long userId, Double amount) {
        // Tạo transactionId và lưu giao dịch vào DB với trạng thái PENDING
        String transactionId = UUID.randomUUID().toString();

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setPaymentMethod("PAYPAL");
        transaction.setStatus("PENDING");
        transaction.setTransactionId(transactionId);
        transactionRepository.save(transaction);

        String accessToken = getAccessToken();
        if (accessToken == null) {
            throw new RuntimeException("Unable to retrieve PayPal access token");
        }

        // Xây dựng payload JSON để tạo payment
        Map<String, Object> paymentPayload = new HashMap<>();
        paymentPayload.put("intent", "sale");

        Map<String, String> redirectUrls = new HashMap<>();
        // Thêm transactionId vào return URL để xử lý callback sau này
        redirectUrls.put("return_url", returnUrl + "?transactionId=" + transactionId);
        redirectUrls.put("cancel_url", cancelUrl + "?transactionId=" + transactionId);
        paymentPayload.put("redirect_urls", redirectUrls);

        Map<String, Object> payer = new HashMap<>();
        payer.put("payment_method", "paypal");
        paymentPayload.put("payer", payer);

        Map<String, Object> amountMap = new HashMap<>();
        // Định dạng số tiền theo chuẩn "xx.xx"
        double conversionRate = 23000.0;
        double usdAmount = amount / conversionRate;
        String total = String.format("%.2f", usdAmount);
        amountMap.put("total", total);
        amountMap.put("currency", "USD");

        Map<String, Object> transactionDetail = new HashMap<>();
        transactionDetail.put("amount", amountMap);
        transactionDetail.put("description", "Nạp tiền vào tài khoản");
        paymentPayload.put("transactions", Collections.singletonList(transactionDetail));

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(paymentPayload, headers);
        String paymentUrl = "https://api.sandbox.paypal.com/v1/payments/payment";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(paymentUrl, httpEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                Map body = response.getBody();
                List<Map> links = (List<Map>) body.get("links");
                for (Map link : links) {
                    if ("approval_url".equals(link.get("rel"))) {
                        // URL chuyển hướng đến trang đăng nhập/cho phép thanh toán của PayPal
                        return link.get("href").toString();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Failed to create PayPal payment");
    }

    public String executePayment(String paymentId, String payerId) {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            throw new RuntimeException("Unable to retrieve PayPal access token");
        }

        // Tạo payload cho API execute
        Map<String, Object> payload = new HashMap<>();
        payload.put("payer_id", payerId);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        String executeUrl = "https://api.sandbox.paypal.com/v1/payments/payment/" + paymentId + "/execute";
        ResponseEntity<Map> response = restTemplate.postForEntity(executeUrl, entity, Map.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            // Nếu execute thành công, trả về "SUCCESS"
            return "SUCCESS";
        }
        return "FAILED";
    }


    public String handlePayPalReturn(String transactionId, String paymentId, String payerId) {
        // Gọi API execute payment
        String executionStatus = executePayment(paymentId, payerId);

        // Tìm giao dịch trong DB và cập nhật trạng thái
        Optional<Transaction> transactionOpt = transactionRepository.findByTransactionId(transactionId);
        if (transactionOpt.isPresent()) {
            Transaction transaction = transactionOpt.get();
            transaction.setStatus("SUCCESS".equalsIgnoreCase(executionStatus) ? "SUCCESS" : "FAILED");
            transactionRepository.save(transaction);
        }
        return "Payment processed via PayPal";
    }

}
