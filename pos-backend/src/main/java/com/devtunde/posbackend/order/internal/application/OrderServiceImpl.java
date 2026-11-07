package com.devtunde.posbackend.order.internal.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.devtunde.posbackend.common.api.validation.PhoneNormalizer;
import com.devtunde.posbackend.customer.api.CustomerService;
import com.devtunde.posbackend.employee.api.EmployeeJobTitle;
import com.devtunde.posbackend.employee.api.EmployeeService;
import com.devtunde.posbackend.employee.api.dto.ActiveEmploymentView;
import com.devtunde.posbackend.inventory.api.InventoryService;
import com.devtunde.posbackend.inventory.api.dto.SaleLine;
import com.devtunde.posbackend.inventory.api.dto.SaleOutcome;
import com.devtunde.posbackend.order.api.OrderService;
import com.devtunde.posbackend.order.api.dto.CheckoutRequest;
import com.devtunde.posbackend.order.api.dto.OrderLineResponse;
import com.devtunde.posbackend.order.api.dto.OrderResponse;
import com.devtunde.posbackend.order.api.dto.ReceiptResponse;
import com.devtunde.posbackend.order.api.dto.ShortfallView;
import com.devtunde.posbackend.order.api.dto.TrayItemResponse;
import com.devtunde.posbackend.order.api.exception.CashGatewayNotAllowedException;
import com.devtunde.posbackend.order.api.exception.CheckoutInvalidException;
import com.devtunde.posbackend.order.api.exception.CheckoutShortfallException;
import com.devtunde.posbackend.order.api.exception.OrderAccessDeniedException;
import com.devtunde.posbackend.order.api.exception.OrderNotFoundException;
import com.devtunde.posbackend.order.api.exception.OrderNotPaidException;
import com.devtunde.posbackend.order.api.exception.SessionConflictException;
import com.devtunde.posbackend.order.internal.KoboFormatter;
import com.devtunde.posbackend.order.internal.domain.Order;
import com.devtunde.posbackend.order.internal.domain.OrderLine;
import com.devtunde.posbackend.order.internal.domain.OrderStatus;
import com.devtunde.posbackend.order.internal.domain.OrderStatusReason;
import com.devtunde.posbackend.order.internal.domain.PaymentMethod;
import com.devtunde.posbackend.order.internal.domain.SessionStatus;
import com.devtunde.posbackend.order.internal.domain.TillSession;
import com.devtunde.posbackend.order.internal.persistence.OrderLineRepository;
import com.devtunde.posbackend.order.internal.persistence.OrderRepository;
import com.devtunde.posbackend.order.internal.persistence.TillSessionRepository;
import com.devtunde.posbackend.payments.api.PaymentProvider;
import com.devtunde.posbackend.product.api.ProductService;
import com.devtunde.posbackend.product.api.dto.StoreProductSaleView;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final TillSessionRepository tillSessionRepository;
    private final OrderNumberAllocator orderNumberAllocator;
    private final EmployeeService employeeService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final StoreService storeService;
    private final UserAccountService userAccountService;
    private final PhoneNormalizer phoneNormalizer;
    private final KoboFormatter koboFormatter;
    private final PendingPaymentService pendingPaymentService;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderLineRepository orderLineRepository,
            TillSessionRepository tillSessionRepository,
            OrderNumberAllocator orderNumberAllocator,
            EmployeeService employeeService,
            ProductService productService,
            InventoryService inventoryService,
            CustomerService customerService,
            StoreService storeService,
            UserAccountService userAccountService,
            PhoneNormalizer phoneNormalizer,
            KoboFormatter koboFormatter,
            PendingPaymentService pendingPaymentService) {

        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.tillSessionRepository = tillSessionRepository;
        this.orderNumberAllocator = orderNumberAllocator;
        this.employeeService = employeeService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.storeService = storeService;
        this.userAccountService = userAccountService;
        this.phoneNormalizer = phoneNormalizer;
        this.koboFormatter = koboFormatter;
        this.pendingPaymentService = pendingPaymentService;
    }

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {

        UserViewResponse caller = userAccountService.currentUser();
        ActiveEmploymentView era = requireCheckoutTitle(caller);
        TillSession session = requireOwnLiveSession(caller);

        PaymentMethod method = parsePaymentMethod(request.paymentMethod());
        PaymentProvider provider = parseProvider(request.paymentProvider());

        if (provider != null && method == PaymentMethod.CASH) {
            throw new CashGatewayNotAllowedException();
        }

        if (provider == PaymentProvider.MONNIFY && method == PaymentMethod.CARD) {
            throw new CheckoutInvalidException("Monnify settles by bank transfer — use TRANSFER with MONNIFY.");
        }

        UUID customerPublicId = resolveCustomer(request.customerPhone());

        Basket basket = snapshotBasket(session, request);
        String orderNumber = orderNumberAllocator.mint();

        if (provider == null) {
            return settleAtCounter(session, customerPublicId, method, request.paymentReference(), orderNumber, basket);
        }

        return openGatewaySlip(session, customerPublicId, method, provider, orderNumber, basket);
    }

    @Override
    @Transactional
    public OrderResponse verifyPayment(UUID publicId) {

        UserViewResponse caller = userAccountService.currentUser();
        Order order = findOrderLocked(publicId);
        TillSession session = findOrderSession(order);
        requireOperatorOf(caller, session);

        List<OrderLineResponse> lines = linesOf(order);
        Order updated = pendingPaymentService.verify(order, session, lines);

        orderRepository.save(updated);
        return toResponse(updated, session, lines);
    }

    @Override
    @Transactional
    public OrderResponse revivePayment(UUID publicId) {

        UserViewResponse caller = userAccountService.currentUser();
        Order order = findOrderLocked(publicId);
        TillSession session = findOrderSession(order);
        requireOperatorOf(caller, session);

        List<OrderLineResponse> lines = linesOf(order);
        Order updated = pendingPaymentService.revive(order);

        orderRepository.save(updated);
        return toResponse(updated, session, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrayItemResponse> pendingTray(String orderNumber) {

        UserViewResponse caller = userAccountService.currentUser();
        TillSession session = requireOwnLiveSession(caller);

        if (orderNumber != null && !orderNumber.isBlank()) {
            Order order =
                    orderRepository.findByOrderNumber(orderNumber.trim()).orElseThrow(OrderNotFoundException::new);

            if (!order.getSessionId().equals(session.getId())) {
                throw new OrderNotFoundException(); // a number from another till is not found, full stop
            }
            return List.of(toTrayItem(order));
        }

        return orderRepository
                .findBySessionIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        session.getId(), OrderStatus.PENDING_PAYMENT, LocalDateTime.now())
                .stream()
                .map(this::toTrayItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID publicId) {

        Order order = findOrder(publicId);
        TillSession session = findOrderSession(order);
        requireReceiptAccess(session);

        return toResponse(order, session, linesOf(order));
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID publicId) {

        Order order = findOrder(publicId);
        TillSession session = findOrderSession(order);
        requireReceiptAccess(session);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new OrderNotPaidException();
        }

        StoreApiResponse store = storeService.getStoreByPublicId(session.getStoreId());
        UserViewResponse cashier = userAccountService.findByPublicId(session.getOperatorPublicId());

        List<ReceiptResponse.Line> lines = orderLineRepository.findByOrderId(order.getId()).stream()
                .map(line -> new ReceiptResponse.Line(
                        line.getProductName(), line.getQuantity(), koboFormatter.format(line.getLineTotalKobo())))
                .toList();

        return new ReceiptResponse(
                order.getPublicId().toString(),
                store.brand(),
                store.contact().address(),
                cashier.fullName(),
                order.getPaymentMethod().name(),
                order.getPaidAt(),
                koboFormatter.format(order.getTotalKobo()),
                lines);
    }

    // ---- checkout legs ----

    private OrderResponse settleAtCounter(
            TillSession session,
            UUID customerPublicId,
            PaymentMethod method,
            String paymentReference,
            String orderNumber,
            Basket basket) {

        SaleOutcome outcome = inventoryService.sellStock(session.getStoreId(), basket.saleLines());

        if (!outcome.sold()) {
            throw new CheckoutShortfallException(outcome.shortfalls().stream()
                    .map(shortfall -> new ShortfallView(
                            shortfall.productPublicId(), shortfall.requested(), shortfall.available()))
                    .toList());
        }

        Order order = Order.paid(session.getId(), customerPublicId, method, paymentReference, basket.totalKobo());
        order.assignOrderNumber(orderNumber);

        saveOrder(order, basket.lines());
        return toResponse(order, session, basket.lines());
    }

    private OrderResponse openGatewaySlip(
            TillSession session,
            UUID customerPublicId,
            PaymentMethod method,
            PaymentProvider provider,
            String orderNumber,
            Basket basket) {

        // No shelf movement here — the shelf moves at settlement, when money has landed.
        Order order = pendingPaymentService.newPendingOrder(session, customerPublicId, method, basket.totalKobo());
        order.assignOrderNumber(orderNumber);

        pendingPaymentService.openGatewayLeg(order, provider, customerPublicId);

        saveOrder(order, basket.lines());
        return toResponse(order, session, basket.lines());
    }

    // ---- gates ----

    private ActiveEmploymentView requireCheckoutTitle(UserViewResponse caller) {

        ActiveEmploymentView era = employeeService
                .activeEraOfUser(caller.publicId())
                .orElseThrow(() -> new OrderAccessDeniedException("No active employment — you cannot check out."));

        if (era.jobTitle() != EmployeeJobTitle.CASHIER && era.jobTitle() != EmployeeJobTitle.MANAGER) {
            throw new OrderAccessDeniedException("Only cashiers and managers may check out.");
        }
        return era;
    }

    private TillSession requireOwnLiveSession(UserViewResponse caller) {

        TillSession session = tillSessionRepository
                .findByOperatorPublicIdAndStatusNot(caller.publicId(), SessionStatus.CLOSED)
                .orElseThrow(() -> new SessionConflictException("Open a session first."));

        if (session.getStatus() == SessionStatus.LOCKED) {
            throw new SessionConflictException("Your session is locked. Resume it before checking out.");
        }
        return session;
    }

    private void requireOperatorOf(UserViewResponse caller, TillSession orderSession) {

        if (!orderSession.getOperatorPublicId().equals(caller.publicId())) {
            throw new OrderAccessDeniedException("Only the cashier who opened this slip may act on it.");
        }
        requireOwnLiveSession(caller);
    }

    private void requireReceiptAccess(TillSession session) {

        UserViewResponse caller = userAccountService.currentUser();

        boolean operator = session.getOperatorPublicId().equals(caller.publicId());
        boolean chainAdmin = caller.role() == UserRole.ROLE_ADMIN;
        boolean seatAdmin = storeService.isStoreAdmin(caller.publicId());

        if (!operator && !chainAdmin && !seatAdmin) {
            throw new OrderAccessDeniedException(
                    "Only the serving operator, a seat admin or the chain admin may read this receipt.");
        }
    }

    // ---- lookup & persistence ----

    private Order findOrder(UUID publicId) {
        return orderRepository.findByPublicId(publicId).orElseThrow(OrderNotFoundException::new);
    }

    private TillSession findOrderSession(Order order) {
        return tillSessionRepository.findById(order.getSessionId()).orElseThrow(OrderNotFoundException::new);
    }

    private Order findOrderLocked(UUID publicId) {

        return orderRepository.findByPublicIdForUpdate(publicId).orElseThrow(OrderNotFoundException::new);
    }

    private void saveOrder(Order order, List<OrderLineResponse> lines) {

        orderRepository.saveAndFlush(order);

        for (OrderLineResponse line : lines) {
            orderLineRepository.save(OrderLine.of(
                    order.getId(), line.productPublicId(), line.productName(), line.unitPriceKobo(), line.quantity()));
        }
    }

    private List<OrderLineResponse> linesOf(Order order) {

        return orderLineRepository.findByOrderId(order.getId()).stream()
                .map(line -> new OrderLineResponse(
                        line.getProductPublicId(),
                        line.getProductName(),
                        line.getUnitPriceKobo(),
                        line.getQuantity(),
                        line.getLineTotalKobo(),
                        koboFormatter.format(line.getUnitPriceKobo()),
                        koboFormatter.format(line.getLineTotalKobo())))
                .toList();
    }

    // ---- snapshot & shaping ----

    private Basket snapshotBasket(TillSession session, CheckoutRequest request) {

        UUID storeId = session.getStoreId();
        Map<UUID, Integer> demandByProduct = new LinkedHashMap<>();
        List<OrderLineResponse> lines = new ArrayList<>();
        long totalKobo = 0L;

        for (CheckoutRequest.Line line : request.lines()) {

            StoreProductSaleView view = productService
                    .findSaleView(storeId, line.productPublicId())
                    .orElseThrow(() -> new CheckoutInvalidException(
                            "Product " + line.productPublicId() + " is not available in this store."));

            long unitPriceKobo = koboOf(view.sellingPrice());
            long lineTotalKobo = unitPriceKobo * line.quantity();
            totalKobo += lineTotalKobo;

            demandByProduct.merge(line.productPublicId(), line.quantity(), Integer::sum);

            lines.add(new OrderLineResponse(
                    line.productPublicId(),
                    view.name(),
                    unitPriceKobo,
                    line.quantity(),
                    lineTotalKobo,
                    koboFormatter.format(unitPriceKobo),
                    koboFormatter.format(lineTotalKobo)));
        }

        return new Basket(lines, totalKobo, demandByProduct);
    }

    private TrayItemResponse toTrayItem(Order order) {

        return new TrayItemResponse(
                order.getPublicId(),
                order.getOrderNumber(),
                order.getProvider() == null ? null : order.getProvider().name(),
                order.getTotalKobo(),
                koboFormatter.format(order.getTotalKobo()),
                order.getExpiresAt());
    }

    private OrderResponse toResponse(Order order, TillSession session, List<OrderLineResponse> lines) {

        String statusName;
        String statusReason;

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && !order.isAlive()) {
            statusName = OrderStatus.EXPIRED.name();
            statusReason = OrderStatusReason.TTL_TIMEOUT.name();
        } else {
            statusName = order.getStatus().name();
            statusReason = order.getStatusReason() == null
                    ? null
                    : order.getStatusReason().name();
        }

        return new OrderResponse(
                order.getPublicId(),
                statusName,
                statusReason,
                order.getOrderNumber(),
                session.getPublicId(),
                session.getOperatorPublicId(),
                session.getStoreId(),
                order.getCustomerPublicId(),
                order.getPaymentMethod().name(),
                order.getPaymentReference(),
                order.getProvider() == null ? null : order.getProvider().name(),
                order.getProvider() == null ? null : order.getPaymentReference(),
                order.getTotalKobo(),
                koboFormatter.format(order.getTotalKobo()),
                order.getPaidAt(),
                order.getExpiresAt(),
                paystackBlock(order),
                monnifyBlock(order),
                lines);
    }

    private static OrderResponse.PaystackBlock paystackBlock(Order order) {

        if (order.getProvider() != PaymentProvider.PAYSTACK || order.getProviderUrl() == null) {
            return null;
        }
        return new OrderResponse.PaystackBlock(order.getProviderUrl());
    }

    private static OrderResponse.MonnifyBlock monnifyBlock(Order order) {

        if (order.getProvider() != PaymentProvider.MONNIFY || order.getProviderAccountNumber() == null) {
            return null;
        }
        return new OrderResponse.MonnifyBlock(
                order.getProviderAccountNumber(), order.getProviderBankName(), order.getProviderBankCode());
    }

    // ---- parsing & resolution ----

    private UUID resolveCustomer(String rawPhone) {

        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }

        String canonical;
        try {
            canonical = phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException ex) {
            throw new CheckoutInvalidException("Customer phone is not a valid phone number.");
        }

        return customerService
                .customerPublicIdByPhone(canonical)
                .orElseThrow(() ->
                        new CheckoutInvalidException("No customer with phone " + rawPhone + ". Enroll them first."));
    }

    private static PaymentMethod parsePaymentMethod(String raw) {

        try {
            return PaymentMethod.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new CheckoutInvalidException("Payment method must be CASH, CARD or TRANSFER.");
        }
    }

    private static PaymentProvider parseProvider(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PaymentProvider.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new CheckoutInvalidException("Payment provider must be PAYSTACK or MONNIFY.");
        }
    }

    private static long koboOf(BigDecimal naira) {
        return naira.movePointRight(2).longValueExact();
    }

    /** The frozen basket: what to show, what to
     * charge, what to sell. */
    private record Basket(List<OrderLineResponse> lines, long totalKobo, Map<UUID, Integer> demandByProduct) {

        List<SaleLine> saleLines() {
            return demandByProduct.entrySet().stream()
                    .map(entry -> new SaleLine(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }
}
