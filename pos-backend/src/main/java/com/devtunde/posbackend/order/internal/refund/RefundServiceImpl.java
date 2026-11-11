package com.devtunde.posbackend.order.internal.refund;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.employee.api.EmployeeJobTitle;
import com.devtunde.posbackend.employee.api.EmployeeService;
import com.devtunde.posbackend.employee.api.dto.ActiveEmploymentView;
import com.devtunde.posbackend.inventory.api.InventoryService;
import com.devtunde.posbackend.inventory.api.dto.ReturnLine;
import com.devtunde.posbackend.order.api.RefundService;
import com.devtunde.posbackend.order.api.dto.RefundLineResponse;
import com.devtunde.posbackend.order.api.dto.RefundRequest;
import com.devtunde.posbackend.order.api.dto.RefundResponse;
import com.devtunde.posbackend.order.api.exception.OrderAccessDeniedException;
import com.devtunde.posbackend.order.api.exception.OrderNotFoundException;
import com.devtunde.posbackend.order.api.exception.OrderNotRefundableException;
import com.devtunde.posbackend.order.api.exception.RefundInvalidException;
import com.devtunde.posbackend.order.api.exception.RefundLineAlreadyRefundedException;
import com.devtunde.posbackend.order.internal.KoboFormatter;
import com.devtunde.posbackend.order.internal.domain.Order;
import com.devtunde.posbackend.order.internal.domain.OrderLine;
import com.devtunde.posbackend.order.internal.domain.OrderStatus;
import com.devtunde.posbackend.order.internal.domain.Refund;
import com.devtunde.posbackend.order.internal.domain.RefundDisposition;
import com.devtunde.posbackend.order.internal.domain.RefundLine;
import com.devtunde.posbackend.order.internal.domain.TillSession;
import com.devtunde.posbackend.order.internal.persistence.OrderLineRepository;
import com.devtunde.posbackend.order.internal.persistence.OrderRepository;
import com.devtunde.posbackend.order.internal.persistence.RefundLineRepository;
import com.devtunde.posbackend.order.internal.persistence.RefundRepository;
import com.devtunde.posbackend.order.internal.persistence.TillSessionRepository;
import com.devtunde.posbackend.payments.api.PaymentGateway;
import com.devtunde.posbackend.payments.api.dto.GatewayReverseRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayReverseResult;
import com.devtunde.posbackend.store.api.StoreService;

@Service
public class RefundServiceImpl implements RefundService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final TillSessionRepository tillSessionRepository;
    private final RefundRepository refundRepository;
    private final RefundLineRepository refundLineRepository;
    private final EmployeeService employeeService;
    private final StoreService storeService;
    private final UserAccountService userAccountService;
    private final InventoryService inventoryService;
    private final PaymentGateway paymentGateway;
    private final KoboFormatter koboFormatter;

    public RefundServiceImpl(
            OrderRepository orderRepository,
            OrderLineRepository orderLineRepository,
            TillSessionRepository tillSessionRepository,
            RefundRepository refundRepository,
            RefundLineRepository refundLineRepository,
            EmployeeService employeeService,
            StoreService storeService,
            UserAccountService userAccountService,
            InventoryService inventoryService,
            PaymentGateway paymentGateway,
            KoboFormatter koboFormatter) {

        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.tillSessionRepository = tillSessionRepository;
        this.refundRepository = refundRepository;
        this.refundLineRepository = refundLineRepository;
        this.employeeService = employeeService;
        this.storeService = storeService;
        this.userAccountService = userAccountService;
        this.inventoryService = inventoryService;
        this.paymentGateway = paymentGateway;
        this.koboFormatter = koboFormatter;
    }

    @Override
    @Transactional
    public RefundResponse refund(UUID orderPublicId, RefundRequest request) {

        UserViewResponse caller = userAccountService.currentUser();

        Order order = orderRepository.findByPublicIdForUpdate(orderPublicId).orElseThrow(OrderNotFoundException::new);
        TillSession session =
                tillSessionRepository.findById(order.getSessionId()).orElseThrow(OrderNotFoundException::new);

        requireRefundAuthority(caller, session);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new OrderNotRefundableException(order.getStatus().name());
        }

        List<OrderLine> orderLines = orderLineRepository.findByOrderId(order.getId());

        Map<UUID, RefundDisposition> dispositionByProduct = new LinkedHashMap<>();

        for (RefundRequest.Line line : request.lines()) {
            if (dispositionByProduct.containsKey(line.productPublicId())) {
                throw new RefundInvalidException(
                        "Product " + line.productPublicId() + " is listed twice in this refund.");
            }

            dispositionByProduct.put(line.productPublicId(), parseDisposition(line.disposition()));
        }

        List<OrderLine> selected = new ArrayList<>();

        for (UUID productPublicId : dispositionByProduct.keySet()) {
            OrderLine line = null;

            for (OrderLine candidate : orderLines) {
                if (candidate.getProductPublicId().equals(productPublicId)) {
                    line = candidate;
                    break;
                }
            }

            if (line == null) {
                throw new RefundInvalidException("Product " + productPublicId + " is not on this order.");
            }

            selected.add(line);
        }

        for (RefundLine alreadyRefunded : refundLineRepository.findByOrderId(order.getId())) {
            if (dispositionByProduct.containsKey(alreadyRefunded.getProductPublicId())) {
                throw new RefundLineAlreadyRefundedException(alreadyRefunded.getProductPublicId());
            }
        }

        boolean gatewaySettled = order.getProvider() != null;
        String returnMethod = null;

        if (gatewaySettled) {
            if (request.returnMethod() != null && !request.returnMethod().isBlank()) {
                throw new RefundInvalidException(
                        "A gateway-settled order is reversed through the provider — no return method needed.");
            }
        } else {
            if (request.returnMethod() == null || request.returnMethod().isBlank()) {
                throw new RefundInvalidException("Record how the money went back: CASH or BANK_TRANSFER.");
            }

            returnMethod = parseReturnMethod(request.returnMethod());
        }

        long totalKobo = 0L;

        for (OrderLine line : selected) {
            totalKobo += line.getLineTotalKobo();
        }

        String providerRefundReference = null;

        if (gatewaySettled) {
            GatewayReverseResult reversal = paymentGateway.reverse(
                    new GatewayReverseRequest(order.getProvider(), order.getPaymentReference(), totalKobo));
            providerRefundReference = reversal.providerRefundReference();
        }

        List<ReturnLine> restocked = new ArrayList<>();

        for (OrderLine line : selected) {
            if (dispositionByProduct.get(line.getProductPublicId()) == RefundDisposition.RESTOCKED) {
                restocked.add(new ReturnLine(line.getProductPublicId(), line.getQuantity()));
            }
        }

        if (!restocked.isEmpty()) {
            inventoryService.returnStock(session.getStoreId(), restocked);
        }

        Refund refund = Refund.of(order.getId(), totalKobo, returnMethod, providerRefundReference, caller.publicId());

        refundRepository.saveAndFlush(refund);

        for (OrderLine line : selected) {
            refundLineRepository.save(RefundLine.of(
                    refund.getId(),
                    order.getId(),
                    line.getProductPublicId(),
                    line.getQuantity(),
                    line.getLineTotalKobo(),
                    dispositionByProduct.get(line.getProductPublicId())));
        }

        return toResponse(refund, order.getPublicId(), selected, dispositionByProduct);
    }

    private void requireRefundAuthority(UserViewResponse caller, TillSession orderSession) {

        if (caller.role() == UserRole.ROLE_ADMIN) {
            return;
        }

        if (storeService.isStoreAdmin(caller.publicId())) {
            return;
        }

        ActiveEmploymentView era =
                employeeService.activeEraOfUser(caller.publicId()).orElse(null);

        if (era != null
                && era.jobTitle() == EmployeeJobTitle.MANAGER
                && era.storePublicId().equals(orderSession.getStoreId())) {
            return;
        }

        throw new OrderAccessDeniedException("Only the store's manager, a seat admin or the chain admin may refund.");
    }

    private RefundDisposition parseDisposition(String raw) {

        if (raw == null || raw.isBlank()) {
            return RefundDisposition.RESTOCKED;
        }

        try {
            return RefundDisposition.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new RefundInvalidException("Disposition must be RESTOCKED or DAMAGED.");
        }
    }

    private String parseReturnMethod(String raw) {

        String value = raw.trim().toUpperCase();

        if (!"CASH".equals(value) && !"BANK_TRANSFER".equals(value)) {
            throw new RefundInvalidException("Return method must be CASH or BANK_TRANSFER.");
        }

        return value;
    }

    private RefundResponse toResponse(
            Refund refund,
            UUID orderPublicId,
            List<OrderLine> selected,
            Map<UUID, RefundDisposition> dispositionByProduct) {

        List<RefundLineResponse> lines = new ArrayList<>();

        for (OrderLine line : selected) {
            lines.add(new RefundLineResponse(
                    line.getProductPublicId(),
                    line.getLineTotalKobo(),
                    dispositionByProduct.get(line.getProductPublicId()).name()));
        }

        return new RefundResponse(
                refund.getPublicId(),
                orderPublicId,
                refund.getTotalKobo(),
                koboFormatter.format(refund.getTotalKobo()),
                lines,
                refund.getReturnMethod(),
                refund.getProviderRefundReference(),
                refund.getRefundedAt());
    }
}
