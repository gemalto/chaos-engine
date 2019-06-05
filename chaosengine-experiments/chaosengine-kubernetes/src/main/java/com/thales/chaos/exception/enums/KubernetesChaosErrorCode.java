package com.thales.chaos.exception.enums;

import com.thales.chaos.exception.ErrorCode;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

public enum KubernetesChaosErrorCode implements ErrorCode {
    K8S_API_ERROR(41000),
    POD_HAS_NO_CONTAINERS(42001),
    K8S_SHELL_CLIENT_ERROR(45000),
    K8S_SHELL_TRANSFER_TIMEOUT(45001),
    K8S_SHELL_TRANSFER_FAIL(45002),

    ;
    private final int errorCode;
    private final String shortName;
    private final String message;
    private ResourceBundle translationBundle;

    KubernetesChaosErrorCode (int errorCode) {
        this.errorCode = errorCode;
        this.shortName = "errorCode." + errorCode + ".name";
        this.message = "errorCode." + errorCode + ".message";
    }

    @Override
    public int getErrorCode () {
        return errorCode;
    }

    @Override
    public String getMessage () {
        return message;
    }

    @Override
    public ResourceBundle getResourceBundle () {
        return Optional.ofNullable(translationBundle).orElseGet(this::initTranslationBundle);
    }

    private synchronized ResourceBundle initTranslationBundle () {
        if (translationBundle != null) return translationBundle;
        try {
            final Locale defaultLocale = Locale.getDefault();
            translationBundle = ResourceBundle.getBundle("exception.ChaosErrorCode", defaultLocale);
        } catch (MissingResourceException e) {
            translationBundle = ResourceBundle.getBundle("exception.ChaosErrorCode", Locale.US);
        }
        return translationBundle;
    }

    @Override
    public String getShortName () {
        return shortName;
    }

    @Override
    public void clearCachedResourceBundle () {
        translationBundle = null;
    }
}