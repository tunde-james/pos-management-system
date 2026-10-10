package com.devtunde.posbackend.product.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductMasterUpdateRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String name,

        @Size(max = 2000) String description,
        @Size(max = 100) String brand,
        @Size(max = 500) String image) {

    public boolean isEmpty() {
        return name == null && description == null && brand == null && image == null;
    }
}
