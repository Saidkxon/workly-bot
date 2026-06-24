package com.advancedprogramming.worklybot.entity.enums;

/**
 * The five fixed departments. The display name is what the user sees and what is
 * stored on the Employee/PendingRegistration as a string, so keep these exact.
 */
public enum Department {

    UNDURUV("Unduruv"),
    PLATFORMA("Platforma"),
    CALL_CENTER("Call Center"),
    QABUL_BOLIMI("Qabul bo'limi"),
    SUPERVISOR("Supervisor");

    private final String displayName;

    Department(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolve a department from its Uzbek display name (case-insensitive, trimmed).
     * Returns null if the value does not match one of the five departments.
     */
    public static Department fromDisplayName(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        for (Department department : values()) {
            if (department.displayName.equalsIgnoreCase(normalized)) {
                return department;
            }
        }
        return null;
    }
}
