package com.devtunde.posbackend.auth.internal.application;

import org.springframework.stereotype.Component;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

@Component
public class PhoneNormalizer {

    private static final PhoneNumberUtil PHONE = PhoneNumberUtil.getInstance();
    private static final String DEFAULT_REGION = "NG";

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            Phonenumber.PhoneNumber parsed = PHONE.parse(raw, DEFAULT_REGION);
            return PHONE.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            throw new IllegalArgumentException("Invalid phone number: " + raw, ex);
        }
    }
}
