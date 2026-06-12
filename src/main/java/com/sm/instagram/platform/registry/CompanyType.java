package com.sm.instagram.platform.registry;

/**
 * Classification of Polish company types based on their legal registration form.
 * Determined from GUS BIR1 legal form codes during NIP verification.
 */
public enum CompanyType {

    /** Jednoosobowa Działalność Gospodarcza — sole proprietorship (CEIDG registry) */
    JDG,

    /** Spółka z ograniczoną odpowiedzialnością — limited liability company (KRS) */
    SP_ZOO,

    /** Spółka akcyjna — joint-stock company (KRS) */
    SA,

    /** Spółka komandytowa — limited partnership (KRS) */
    SP_K,

    /** Spółka jawna — general partnership (KRS) */
    SP_J,

    /** Any other KRS-registered entity */
    OTHER_KRS;

    /**
     * Returns true if this company type is registered in KRS (not CEIDG).
     */
    public boolean isKrsEntity() {
        return this != JDG;
    }
}
