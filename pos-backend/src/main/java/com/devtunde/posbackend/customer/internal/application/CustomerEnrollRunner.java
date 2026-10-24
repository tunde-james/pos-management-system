package com.devtunde.posbackend.customer.internal.application;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.common.api.validation.PhoneNormalizer;
import com.devtunde.posbackend.customer.api.dto.CustomerEnrollRequest;
import com.devtunde.posbackend.customer.api.dto.CustomerEnrollResult;
import com.devtunde.posbackend.customer.api.dto.CustomerResponse;
import com.devtunde.posbackend.customer.internal.domain.Customer;
import com.devtunde.posbackend.customer.internal.persistence.CustomerRepository;

@Service
public class CustomerEnrollRunner {

    private final CustomerRepository customerRepository;
    private final PhoneNormalizer phoneNormalizer;

    public CustomerEnrollRunner(CustomerRepository customerRepository, PhoneNormalizer phoneNormalizer) {

        this.customerRepository = customerRepository;
        this.phoneNormalizer = phoneNormalizer;
    }

    @Transactional
    public CustomerEnrollResult enroll(CustomerEnrollRequest request) {

        String phone = phoneNormalizer.normalize(request.phone());

        Customer exisiting = customerRepository.findByPhone(phone).orElse(null);

        if (exisiting != null) {
            return new CustomerEnrollResult(toResponse(exisiting), false);
        }

        Customer customer = Customer.enroll(request.fullName(), phone, request.email(), mintTag());

        try {
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomerEnrollRaceException();
        }

        return new CustomerEnrollResult(toResponse(customer), true);
    }

    private String mintTag() {

        String tag;

        do {
            tag = String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (customerRepository.existsByLoyaltyTag(tag));

        return tag;
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
