package com.sateno_b.www.model.enums;

/**
 * По какъв признак е свързан анонимен посетител с реален клиент
 * (слой за идентичност на AI Sales Assistant).
 *
 * Автоматично свързване само при 100% сигурност: PHONE / EMAIL / ORDER (решение §1.3).
 * LOGIN е опция за после (при акаунти).
 */
public enum IdentityMatchType {
    PHONE,
    EMAIL,
    ORDER,
    LOGIN
}
