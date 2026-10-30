package com.devtunde.posbackend.order.internal.application;

import java.math.BigDecimal;
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
import com.devtunde.posbackend.order.api.exception.CheckoutInvalidException;
import com.devtunde.posbackend.order.api.exception.CheckoutShortfallException;
import com.devtunde.posbackend.order.api.exception.OrderAccessDeniedException;
import com.devtunde.posbackend.order.api.exception.OrderNotFoundException;
import com.devtunde.posbackend.order.api.exception.SessionConflictException;
import com.devtunde.posbackend.order.internal.KoboFormatter;
import com.devtunde.posbackend.order.internal.domain.Order;
import com.devtunde.posbackend.order.internal.domain.OrderLine;
import com.devtunde.posbackend.order.internal.domain.PaymentMethod;
import com.devtunde.posbackend.order.internal.domain.SessionStatus;
import com.devtunde.posbackend.order.internal.domain.TillSession;
import com.devtunde.posbackend.order.internal.persistence.OrderLineRepository;
import com.devtunde.posbackend.order.internal.persistence.OrderRepository;
import com.devtunde.posbackend.order.internal.persistence.TillSessionRepository;
import com.devtunde.posbackend.product.api.ProductService;
import com.devtunde.posbackend.product.api.dto.StoreProductSaleView;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final TillSessionRepository tillSessionRepository;
    private final EmployeeService employeeService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final StoreService storeService;
    private final UserAccountService userAccountService;
    private final PhoneNormalizer phoneNormalizer;
    private final KoboFormatter koboFormatter;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderLineRepository orderLineRepository,
            TillSessionRepository tillSessionRepository,
            EmployeeService employeeService,
            ProductService productService,
            InventoryService inventoryService,
            CustomerService customerService,
            StoreService storeService,
            UserAccountService userAccountService,
            PhoneNormalizer phoneNormalizer,
            KoboFormatter koboFormatter) {

        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.tillSessionRepository = tillSessionRepository;
        this.employeeService = employeeService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.storeService = storeService;
        this.userAccountService = userAccountService;
        this.phoneNormalizer = phoneNormalizer;
        this.koboFormatter = koboFormatter;
    }

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {

        UserViewResponse caller = userAccountService.currentUser();

        ActiveEmploymentView era = employeeService
                .activeEraOfUser(caller.publicId())
                .orElseThrow(() -> new OrderAccessDeniedException("No active employment — you cannot check out."));

        if (era.jobTitle() != EmployeeJobTitle.CASHIER && era.jobTitle() != EmployeeJobTitle.MANAGER) {
            throw new OrderAccessDeniedException("Only cashiers and managers may check out.");
        }

        TillSession session = tillSessionRepository
                .findByOperatorPublicIdAndStatusNot(caller.publicId(), SessionStatus.CLOSED)
                .orElseThrow(() -> new SessionConflictException("Open a session first."));

        if (session.getStatus() == SessionStatus.LOCKED) {
            throw new SessionConflictException("Your session is locked. Resume it before checking out.");
        }

        PaymentMethod method = parsePaymentMethod(request.paymentMethod());

        UUID customerPublicId = resolveCustomer(request.customerPhone());

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

        SaleOutcome outcome = inventoryService.sellStock(
                storeId,
                demandByProduct.entrySet().stream()
                        .map(entry -> new SaleLine(entry.getKey(), entry.getValue()))
                        .toList());

        if (!outcome.sold()) {
            throw new CheckoutShortfallException(outcome.shortfalls().stream()
                    .map(s -> new ShortfallView(s.productPublicId(), s.requested(), s.available()))
                    .toList());
        }

        Order order = Order.paid(session.getId(), customerPublicId, method, request.paymentReference(), totalKobo);

        orderRepository.saveAndFlush(order);

        for (OrderLineResponse line : lines) {
            orderLineRepository.save(OrderLine.of(
                    order.getId(), line.productPublicId(), line.productName(), line.unitPriceKobo(), line.quantity()));
        }

        return toResponse(order, session, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID publicId) {

        Order order = orderRepository.findByPublicId(publicId).orElseThrow(OrderNotFoundException::new);

        TillSession session =
                tillSessionRepository.findById(order.getSessionId()).orElseThrow(OrderNotFoundException::new);

        requireReceiptAccess(session);

        List<OrderLineResponse> lines = orderLineRepository.findByOrderId(order.getId()).stream()
                .map(l -> new OrderLineResponse(
                        l.getProductPublicId(),
                        l.getProductName(),
                        l.getUnitPriceKobo(),
                        l.getQuantity(),
                        l.getLineTotalKobo(),
                        koboFormatter.format(l.getUnitPriceKobo()),
                        koboFormatter.format(l.getLineTotalKobo())))
                .toList();

        return toResponse(order, session, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID publicId) {

        Order order = orderRepository.findByPublicId(publicId).orElseThrow(OrderNotFoundException::new);

        TillSession session =
                tillSessionRepository.findById(order.getSessionId()).orElseThrow(OrderNotFoundException::new);

        requireReceiptAccess(session);

        StoreApiResponse store = storeService.getStoreByPublicId(session.getStoreId());
        UserViewResponse cashier = userAccountService.findByPublicId(session.getOperatorPublicId());

        List<ReceiptResponse.Line> lines = orderLineRepository.findByOrderId(order.getId()).stream()
                .map(l -> new ReceiptResponse.Line(
                        l.getProductName(), l.getQuantity(), koboFormatter.format(l.getLineTotalKobo())))
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

    private static PaymentMethod parsePaymentMethod(String raw) {

        try {
            return PaymentMethod.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new CheckoutInvalidException("Payment method must be CASH, CARD or TRANSFER.");
        }
    }

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

    private static long koboOf(BigDecimal naira) {

        return naira.movePointRight(2).longValueExact();
    }

    private OrderResponse toResponse(Order order, TillSession session, List<OrderLineResponse> lines) {

        return new OrderResponse(
                order.getPublicId(),
                order.getStatus().name(),
                session.getPublicId(),
                session.getOperatorPublicId(),
                session.getStoreId(),
                order.getCustomerPublicId(),
                order.getPaymentMethod().name(),
                order.getPaymentReference(),
                order.getTotalKobo(),
                koboFormatter.format(order.getTotalKobo()),
                order.getPaidAt(),
                lines);
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
}
