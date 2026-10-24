package com.devtunde.posbackend.customer.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.customer.api.dto.CustomerEnrollRequest;
import com.devtunde.posbackend.customer.api.dto.CustomerEnrollResult;
import com.devtunde.posbackend.customer.api.dto.CustomerResponse;

public interface CustomerService {

    CustomerEnrollResult enrollCustomer(CustomerEnrollRequest request);

    CustomerResponse getCustomerByPublicId(UUID publicId);

    CustomerResponse getCustomerByPhone(String phone);

    List<CustomerResponse> listCustomers();
}
