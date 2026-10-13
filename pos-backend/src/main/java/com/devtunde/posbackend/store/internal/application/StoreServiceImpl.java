package com.devtunde.posbackend.store.internal.application;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.exception.UserNotFoundException;
import com.devtunde.posbackend.common.api.validation.PhoneNormalizer;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;
import com.devtunde.posbackend.store.api.dto.StoreRequest;
import com.devtunde.posbackend.store.api.dto.StoreUpdateRequest;
import com.devtunde.posbackend.store.api.exception.InvalidStoreHoursException;
import com.devtunde.posbackend.store.api.exception.StoreAdminNotFoundException;
import com.devtunde.posbackend.store.api.exception.StoreAlreadyExistsException;
import com.devtunde.posbackend.store.api.exception.StoreClosureNotPermittedException;
import com.devtunde.posbackend.store.api.exception.StoreNotFoundException;
import com.devtunde.posbackend.store.internal.domain.Store;
import com.devtunde.posbackend.store.internal.mapper.StoreMapper;
import com.devtunde.posbackend.store.internal.persistence.StoreRepository;

@Service
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final UserAccountService userAccountService;
    private final PhoneNormalizer phoneNormalizer;
    private final StoreMapper storeMapper;

    public StoreServiceImpl(
            StoreRepository storeRepository,
            UserAccountService userAccountService,
            PhoneNormalizer phoneNormalizer,
            StoreMapper storeMapper) {
        this.storeRepository = storeRepository;
        this.userAccountService = userAccountService;
        this.phoneNormalizer = phoneNormalizer;
        this.storeMapper = storeMapper;
    }

    @Override
    public StoreApiResponse createStore(StoreRequest request) {

        UUID adminId = resolveAdmin(request.storeAdminPublicId());

        validateHours(request.openTime(), request.closeTime());

        storeRepository
                .findByStoreAdminId(adminId)
                .filter(other -> other.getStatus() != StoreStatus.CLOSED)
                .ifPresent(other -> {
                    throw new StoreAlreadyExistsException("store admin");
                });

        if (storeRepository.findByBrand(request.brand()).isPresent()) {
            throw new StoreAlreadyExistsException("brand");
        }

        if (storeRepository.findByContact_Email(request.contact().email()).isPresent()) {
            throw new StoreAlreadyExistsException("contact email");
        }

        String phone = phoneNormalizer.normalize(request.contact().phone());

        Store store = Store.create(
                request.brand(),
                request.description(),
                request.storeType(),
                request.contact().address(),
                phone,
                request.contact().email(),
                adminId);

        store.setOpenTime(request.openTime());
        store.setCloseTime(request.closeTime());
        store.setWorkingDays(request.workingDays());

        return storeMapper.toResponse(storeRepository.save(store));
    }

    @Override
    public StoreApiResponse getStoreByPublicId(UUID publicId) {

        Store store = findOpenStore(publicId);

        return storeMapper.toResponse(store);
    }

    @Override
    public List<StoreApiResponse> getAllStores() {
        return storeRepository.findAll().stream()
                .filter(store -> store.getStatus() != StoreStatus.CLOSED)
                .map(storeMapper::toResponse)
                .toList();
    }

    @Override
    public StoreApiResponse getMyStore() {

        UUID callerId = userAccountService.currentUser().publicId();

        Store store = storeRepository
                .findByStoreAdminId(callerId)
                .filter(status -> status.getStatus() != StoreStatus.CLOSED)
                .orElseThrow(StoreNotFoundException::new);

        return storeMapper.toResponse(store);
    }

    @Override
    public StoreApiResponse updateStore(UUID publicId, StoreUpdateRequest request) {

        Store store = findOpenStore(publicId);

        if (request.status() == StoreStatus.CLOSED) {
            throw new StoreClosureNotPermittedException();
        }

        validateHours(request.openTime(), request.closeTime());

        if (request.brand() != null) {
            storeRepository.findByBrand(request.brand()).ifPresent(other -> {
                if (!other.getPublicId().equals(publicId)) {
                    throw new StoreAlreadyExistsException("brand");
                }
            });
        }

        if (request.contact() != null) {
            storeRepository.findByContact_Email(request.contact().email()).ifPresent(other -> {
                if (!other.getPublicId().equals(publicId)) {
                    throw new StoreAlreadyExistsException("contact email");
                }
            });
        }

        if (request.storeAdminPublicId() != null) {
            UUID newAdminId = resolveAdmin(request.storeAdminPublicId());

            storeRepository
                    .findByStoreAdminId(newAdminId)
                    .filter(other -> other.getStatus() != StoreStatus.CLOSED)
                    .ifPresent(other -> {
                        if (!other.getPublicId().equals(publicId)) {
                            throw new StoreAlreadyExistsException("store admin");
                        }
                    });
        }

        storeMapper.update(request, store);

        if (request.contact() != null) {
            store.getContact()
                    .setPhone(phoneNormalizer.normalize(request.contact().phone()));
        }

        return storeMapper.toResponse(storeRepository.save(store));
    }

    @Override
    public boolean isStoreAdmin(UUID userPublicId) {
        return storeRepository
                .findByStoreAdminId(userPublicId)
                .filter(store -> store.getStatus() == StoreStatus.ACTIVE)
                .isPresent();
    }

    @Override
    public void deleteStore(UUID publicId) {

        Store store = findOpenStore(publicId);

        store.setLastStoreAdminId(store.getStoreAdminId());
        store.setStatus(StoreStatus.CLOSED);
        store.setStoreAdminId(null);

        storeRepository.save(store);
    }

    private UUID resolveAdmin(UUID storeAdminPublicId) {
        try {
            userAccountService.findByPublicId(storeAdminPublicId);

            return storeAdminPublicId;
        } catch (UserNotFoundException ex) {
            throw new StoreAdminNotFoundException();
        }
    }

    private Store findOpenStore(UUID publicId) {
        Store store = storeRepository.findByPublicId(publicId).orElseThrow(StoreNotFoundException::new);

        if (store.getStatus() == StoreStatus.CLOSED) {
            throw new StoreNotFoundException();
        }

        return store;
    }

    private void validateHours(LocalTime openTime, LocalTime closeTime) {
        if (openTime != null && closeTime != null && !closeTime.isAfter(openTime)) {
            throw new InvalidStoreHoursException();
        }
    }
}
