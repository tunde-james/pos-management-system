package com.devtunde.posbackend.customer.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.customer.api.dto.CustomerEnrollRequest;
import com.devtunde.posbackend.customer.api.dto.CustomerEnrollResult;
import com.devtunde.posbackend.customer.api.dto.CustomerResponse;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> enrollCustomer(@Valid @RequestBody CustomerEnrollRequest request) {

        CustomerEnrollResult result = customerService.enrollCustomer(request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(result.customer());
    }

    @GetMapping("/{publicId}")
    public CustomerResponse getCustomerByPublicId(@PathVariable("publicId") UUID publicId) {

        return customerService.getCustomerByPublicId(publicId);
    }

    @GetMapping("/phone/{phone}")
    public CustomerResponse getCustomerByPhone(@PathVariable("phone") String phone) {

        return customerService.getCustomerByPhone(phone);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<CustomerResponse> listCustomers() {

        return customerService.listCustomers();
    }
}
