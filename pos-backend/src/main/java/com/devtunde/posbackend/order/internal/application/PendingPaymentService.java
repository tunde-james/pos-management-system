package com.devtunde.posbackend.order.internal.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devtunde.posbackend.customer.api.CustomerService;
import com.devtunde.posbackend.customer.api.dto.CustomerContact;
import com.devtunde.posbackend.inventory.api.InventoryService;
import com.devtunde.posbackend.inventory.api.dto.SaleLine;
import com.devtunde.posbackend.inventory.api.dto.SaleOutcome;
import com.devtunde.posbackend.order.api.dto.OrderLineResponse;
import com.devtunde.posbackend.order.api.exception.AmountMismatchException;
import com.devtunde.posbackend.order.api.exception.OrderAlreadySettledException;
import com.devtunde.posbackend.order.api.exception.OrderNotGatewaySettledException;
import com.devtunde.posbackend.order.api.exception.OrderNotRevivableException;
import com.devtunde.posbackend.order.api.exception.TransferAccountExpiredException;
import com.devtunde.posbackend.order.internal.domain.Order;
import com.devtunde.posbackend.order.internal.domain.OrderStatus;
import com.devtunde.posbackend.order.internal.domain.OrderStatusReason;
import com.devtunde.posbackend.order.internal.domain.PaymentMethod;
import com.devtunde.posbackend.order.internal.domain.TillSession;
import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;
import com.devtunde.posbackend.payments.api.PaymentGateway;
import com.devtunde.posbackend.payments.api.PaymentProvider;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateResult;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyResult;

@Component
public class PendingPaymentService {

    private final PaymentGateway paymentGateway;
    private final InventoryService inventoryService;
    private final CustomerService customerService;

    @Value("${app.payments.pending-ttl}")
    private Duration pendingTtl;

    public PendingPaymentService(
            PaymentGateway paymentGateway, InventoryService inventoryService, CustomerService customerService) {

        this.paymentGateway = paymentGateway;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
    }

    public Order newPendingOrder(TillSession session, UUID customerPublicId, PaymentMethod method, long totalKobo) {

        return Order.pendingPayment(
                session.getId(),
                customerPublicId,
                method,
                totalKobo,
                LocalDateTime.now().plus(pendingTtl));
    }

    public void openGatewayLeg(Order pendingOrder, PaymentProvider provider, UUID customerPublicId) {

        CustomerContact contact =
                customerPublicId == null ? null : customerService.customerContactByPublicId(customerPublicId);
        String customerEmail = contact == null ? null : contact.email();
        String customerName = contact == null ? null : contact.fullName();

        GatewayInitiateResult initiated = paymentGateway.initiate(new GatewayInitiateRequest(
                provider, pendingOrder.getOrderNumber(), pendingOrder.getTotalKobo(), customerEmail, customerName));

        pendingOrder.attachGateway(
                provider,
                initiated.providerReference(),
                initiated.authorizationUrl(),
                initiated.accountNumber(),
                initiated.bankName(),
                initiated.bankCode(),
                initiated.accountExpiresAt());
    }

    public Order verify(Order order, TillSession session, List<OrderLineResponse> lines) {

        if (order.getProvider() == null) {
            throw new OrderNotGatewaySettledException();
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderAlreadySettledException(order.getStatus().name());
        }

        if (!order.isAlive()) {

            order.markExpired(OrderStatusReason.TTL_TIMEOUT);
            return order;
        }

        GatewayVerifyResult result = paymentGateway.verify(
                new GatewayVerifyRequest(order.getProvider(), order.getPaymentReference(), order.getTotalKobo()));

        if (result.status() == GatewayVerifyStatus.SUCCESS) {
            return settleFromGateway(order, session, lines, result);
        }
        if (result.status() == GatewayVerifyStatus.FAILED) {

            order.markFailed(OrderStatusReason.GATEWAY_DECLINED);
            return order;
        }
        return order;
    }

    public Order revive(Order order) {

        if (order.getProvider() == null) {
            throw new OrderNotRevivableException("Counter-settled orders have nothing to revive.");
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            if (order.isAlive()) {
                throw new OrderNotRevivableException("This slip is still alive — nothing to revive.");
            }

            order.reviveUntil(revivalExpiryOf(order));
            return order;
        }

        if (order.getStatus() == OrderStatus.EXPIRED && order.getStatusReason() == OrderStatusReason.TTL_TIMEOUT) {

            order.reviveUntil(revivalExpiryOf(order));
            return order;
        }

        throw new OrderNotRevivableException(
                "Only TTL-expired slips can be revived (this one is " + order.getStatus() + ").");
    }

    private LocalDateTime revivalExpiryOf(Order order) {

        LocalDateTime until = LocalDateTime.now().plus(pendingTtl);

        if (order.getProvider() != PaymentProvider.MONNIFY) {
            return until;
        }
        LocalDateTime accountExpiresAt = order.getProviderAccountExpiresAt();
        if (accountExpiresAt == null) {
            return until;
        }
        if (!accountExpiresAt.isAfter(LocalDateTime.now())) {
            throw new TransferAccountExpiredException();
        }
        return accountExpiresAt.isBefore(until) ? accountExpiresAt : until;
    }

    private Order settleFromGateway(
            Order order, TillSession session, List<OrderLineResponse> lines, GatewayVerifyResult result) {

        if (result.amountKobo() != order.getTotalKobo()) {
            throw new AmountMismatchException(order.getTotalKobo(), result.amountKobo());
        }

        SaleOutcome outcome = inventoryService.sellStock(session.getStoreId(), saleLinesOf(lines));
        if (!outcome.sold()) {

            order.markExpired(OrderStatusReason.STOCK_GONE_AT_SETTLEMENT);
            return order;
        }
        order.markPaid();
        return order;
    }

    private static List<SaleLine> saleLinesOf(List<OrderLineResponse> lines) {

        return lines.stream()
                .map(line -> new SaleLine(line.productPublicId(), line.quantity()))
                .toList();
    }
}
