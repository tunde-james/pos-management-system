package com.devtunde.posbackend.order.internal;

import org.springframework.stereotype.Component;

import com.devtunde.posbackend.common.api.MoneyProperties;

@Component
public final class KoboFormatter {

    private final String symbol;

    public KoboFormatter(MoneyProperties moneyProperties) {
        this.symbol = moneyProperties.currencySymbol();
    }

    public String format(long minorUnits) {

        long major = minorUnits / 100;
        long minor = minorUnits % 100;

        return symbol + major + "." + String.format("%02d", minor);
    }
}
