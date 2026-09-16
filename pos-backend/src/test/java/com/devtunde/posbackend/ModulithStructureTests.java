package com.devtunde.posbackend;

import org.springframework.modulith.core.ApplicationModules;

import org.junit.jupiter.api.Test;

class ModulithStructureTests {

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(PosBackendApplication.class).verify();
    }
}
