@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "auth::api",
            "common::api",
            "customer::api",
            "employee::api",
            "inventory::api",
            "product::api",
            "store::api"
        })
package com.devtunde.posbackend.order;
