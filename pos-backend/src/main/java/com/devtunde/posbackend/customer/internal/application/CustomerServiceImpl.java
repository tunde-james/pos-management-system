package com.devtunde.posbackend.customer.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.customer.api.CustomerService;
import com.devtunde.posbackend.customer.api.dto.CustomerEnrollRequest;
import com.devtunde.posbackend.customer.api.dto.CustomerEnrollResult;
import com.devtunde.posbackend.customer.api.dto.CustomerResponse;
import com.devtunde.posbackend.customer.api.exception.CustomerNotFoundException;
import com.devtunde.posbackend.customer.internal.domain.Customer;
import com.devtunde.posbackend.customer.internal.persistence.CustomerRepository;
import com.devtunde.posbackend.store.api.StoreService;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerEnrollRunner enrollRunner;
    private final UserAccountService userAccountService;
    private final StoreService storeService;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            CustomerEnrollRunner enrollRunner,
            UserAccountService userAccountService,
            StoreService storeService) {

        this.customerRepository = customerRepository;
        this.enrollRunner = enrollRunner;
        this.userAccountService = userAccountService;
        this.storeService = storeService;
    }

    @Override
    public CustomerEnrollResult enrollCustomer(CustomerEnrollRequest request) {

        requireAnySeatAdminOrChainAdmin();

        try {
            return enrollRunner.enroll(request);
        } catch (CustomerEnrollRaceException ex) {
            return enrollRunner.enroll(request);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByPublicId(UUID publicId) {

        requireAnySeatAdminOrChainAdmin();

        return toResponse(customerRepository.findByPublicId(publicId).orElseThrow(CustomerNotFoundException::new));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByPhone(String phone) {

        requireAnySeatAdminOrChainAdmin();

        return toResponse(customerRepository.findByPhone(phone).orElseThrow(CustomerNotFoundException::new));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> listCustomers() {

        return customerRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireAnySeatAdminOrChainAdmin() {

        UserViewResponse currentUser = userAccountService.currentUser();

        if (currentUser.role() == UserRole.ROLE_ADMIN) {
            return;
        }

        if (storeService.isStoreAdmin(currentUser.publicId())) {
            return;
        }

        throw new AccessDeniedException("Only the chain admin or a store admin may access customer records");
    }

    private CustomerResponse toResponse(Customer customer) {

        return new CustomerResponse(
                customer.getPublicId(),
                customer.getLoyaltyTag(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getCreatedAt());
    }
}
