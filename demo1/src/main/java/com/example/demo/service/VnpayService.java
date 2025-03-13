package com.example.demo.service;

import com.example.demo.config.VnpayConfig;
import com.example.demo.model.Transaction;
import com.example.demo.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VnpayService {

    @Autowired
    private TransactionRepository transactionRepository;

    public String createPaymentUrl(Long userId, Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
        }

        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";

        long amountForVNPay = Math.round(amount * 100); // VNPay yêu cầu số tiền nhân 100

        // Tạo mã giao dịch ngẫu nhiên
        String vnp_TxnRef = VnpayConfig.getRandomNumber(8);

        // Lưu giao dịch với trạng thái PENDING
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setPaymentMethod("VNPAY");
        transaction.setStatus("PENDING");
        transaction.setVnpTxnRef(vnp_TxnRef);
        transactionRepository.save(transaction);

        // Tạo URL thanh toán VNPay
        String vnp_TmnCode = VnpayConfig.vnp_TmnCode;
        String vnp_IpAddr = "127.0.0.1"; // Có thể lấy IP thực tế từ request

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountForVNPay));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Nạp tiền vào tài khoản: " + userId);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", VnpayConfig.vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        // Thời gian tạo giao dịch và hết hạn (15 phút)
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));

        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        // Tạo chuỗi ký số
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append('&');
                hashData.append('&');
            }
        }
        if (query.length() > 0) {
            query.setLength(query.length() - 1);
        }
        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }

        String vnp_SecureHash = VnpayConfig.hmacSHA512(VnpayConfig.secretKey, hashData.toString());
        query.append("&vnp_SecureHash=").append(vnp_SecureHash);

        return VnpayConfig.vnp_PayUrl + "?" + query;
    }

    public ResponseEntity<String> handleVnPayReturn(String responseCode, String vnp_TxnRef) {
        if("00".equals(responseCode)) {
            Transaction transaction = transactionRepository.findByVnpTxnRef(vnp_TxnRef);
            if (transaction != null) {
                transaction.setStatus("SUCCESS");
                transactionRepository.save(transaction);
                return ResponseEntity.ok("Thanh toán thành công!");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Giao dịch không tồn tại!");
            }
        } else {
            Transaction transaction = transactionRepository.findByVnpTxnRef(vnp_TxnRef);
            if (transaction != null) {
                transaction.setStatus("FAILED");
                transactionRepository.save(transaction);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Thanh toán thất bại! Mã lỗi: " + responseCode);
        }
    }
}
