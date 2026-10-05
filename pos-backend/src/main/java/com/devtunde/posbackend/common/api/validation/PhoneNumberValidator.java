package com.devtunde.posbackend.common.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.stereotype.Component;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

@Component
public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    @Override
    public void initialize(ValidPhoneNumber constraintAnnotation) {}

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {

        if (phone == null || phone.isBlank()) {
            return true;
        }

        String regionCode = "NG";

        try {
            Phonenumber.PhoneNumber parsedNumber = PHONE_NUMBER_UTIL.parse(phone, regionCode);

            return PHONE_NUMBER_UTIL.isValidNumber(parsedNumber);
        } catch (NumberParseException ex) {
            return false;
        }
    }
}
