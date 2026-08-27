package com.tabish.SpringBootLab.service;

import com.tabish.SpringBootLab.config.KafkaTopicConfig;
import com.tabish.SpringBootLab.event.PaymentPaidEvent;
import com.tabish.SpringBootLab.dto.CreatePaymentRequest;
import com.tabish.SpringBootLab.dto.PaymentResponse;
import com.tabish.SpringBootLab.exception.IdempotencyConflictException;
import com.tabish.SpringBootLab.exception.InvalidIdempotencyKeyException;
import com.tabish.SpringBootLab.exception.IdempotencyKeyRequiredException;
import com.tabish.SpringBootLab.exception.InvalidOrderStatusException;
import com.tabish.SpringBootLab.exception.OrderAlreadyPaidException;
import com.tabish.SpringBootLab.exception.OrderNotFoundException;
import com.tabish.SpringBootLab.exception.PaymentNotFoundException;
import com.tabish.SpringBootLab.model.IdempotencyRecord;
import com.tabish.SpringBootLab.model.Order;
import com.tabish.SpringBootLab.model.OrderStatus;
import com.tabish.SpringBootLab.model.Payment;
import com.tabish.SpringBootLab.model.PaymentStatus;
import com.tabish.SpringBootLab.repository.IdempotencyRecordRepository;
import com.tabish.SpringBootLab.repository.OrderRepository;
import com.tabish.SpringBootLab.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventService outboxEventService;
    private final long idempotencyRetentionHours;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OutboxEventService outboxEventService,
            @Value("${payment.idempotency.retention-hours:24}") long idempotencyRetentionHours
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventService = outboxEventService;
        this.idempotencyRetentionHours = Math.max(1, idempotencyRetentionHours);
    }

    @Transactional
    public PaymentResponse payOrder(
        Integer orderId, 
        CreatePaymentRequest request,
        String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }

        String normalizedIdempotencyKey = idempotencyKey.trim();
        if (normalizedIdempotencyKey.length() > 255) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key cannot exceed 255 characters"
            );
        }

        String requestPath = "/api/orders/" + orderId + "/payments";
        String requestFingerprint = fingerprint(request);

        IdempotencyRecord existingRecord = idempotencyRecordRepository
                .findByIdempotencyKeyAndRequestPath(normalizedIdempotencyKey, requestPath)
                .orElse(null);

        if (existingRecord != null) {
            return replayExistingPayment(existingRecord, requestFingerprint, orderId);
        }
        
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        existingRecord = idempotencyRecordRepository
                .findByIdempotencyKeyAndRequestPath(normalizedIdempotencyKey, requestPath)
                .orElse(null);

        if (existingRecord != null) {
            return replayExistingPayment(existingRecord, requestFingerprint, orderId);
        }

        if (order.getStatus() == OrderStatus.PAID) {
            throw new OrderAlreadyPaidException(orderId);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                    "Only PENDING orders can be paid. Current status: " + order.getStatus()
            );
        }

        if (paymentRepository.existsByOrder(order)) {
            throw new OrderAlreadyPaidException(orderId);
        }

        Payment payment = new Payment(
                order,
                order.getTotalAmount(),
                PaymentStatus.PAID,
                request.getMethod()
        );

        payment.setPaidAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        IdempotencyRecord record = new IdempotencyRecord(
                normalizedIdempotencyKey,
                requestPath,
                requestFingerprint,
                savedPayment.getId(),
                200,
                LocalDateTime.now().plusHours(idempotencyRetentionHours)
        );
        
        idempotencyRecordRepository.save(record);
        
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        outboxEventService.saveEvent(
                "PAYMENT",
                String.valueOf(savedPayment.getId()),
                "PAYMENT_PAID",
                KafkaTopicConfig.PAYMENT_PAID_TOPIC,
                new PaymentPaidEvent(
                        savedPayment.getId(),
                        order.getId(),
                        savedPayment.getAmount(),
                        savedPayment.getMethod().name(),
                        savedPayment.getPaidAt()
                )
        );

        return toPaymentResponse(savedPayment);
    }

    public PaymentResponse getPaymentByOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        return toPaymentResponse(payment);
    }

    private PaymentResponse replayExistingPayment(
            IdempotencyRecord record,
            String requestFingerprint,
            Integer orderId
    ) {
        if (!requestFingerprint.equals(record.getRequestFingerprint())) {
            throw new IdempotencyConflictException();
        }

        Payment existingPayment = paymentRepository.findById(record.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        return toPaymentResponse(existingPayment);
    }

    private String fingerprint(CreatePaymentRequest request) {
        String canonicalRequest = "method=" + request.getMethod().name();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
