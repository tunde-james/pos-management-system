package com.devtunde.posbackend.auth.internal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.auth.internal.domain.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "publicId", source = "publicId")
    UserViewResponse toView(User user);
}
