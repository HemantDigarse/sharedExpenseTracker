package com.spreetail.expenses.currency;

/**
 * Enum representing supported currencies in the application.
 *
 * <p>Currently supports:
 * <ul>
 *   <li>{@code INR} — Indian Rupee (base currency for all calculations)</li>
 *   <li>{@code USD} — US Dollar (used by Dev during the trip)</li>
 * </ul>
 *
 * <p>This enum maps to the PostgreSQL ENUM type 'currency' created
 * in Flyway migration V3. Adding a new currency requires:
 * <ol>
 *   <li>A Flyway migration to ALTER TYPE currency ADD VALUE 'NEW_CURRENCY'</li>
 *   <li>Adding the value to this enum</li>
 *   <li>Seeding an exchange rate in the exchange_rates table</li>
 * </ol>
 *
 * <p>DESIGN DECISION: All balance calculations are performed in INR.
 * Non-INR expenses are converted to INR at import/creation time and
 * stored in the {@code amount_in_inr} column. This avoids mixing
 * currencies during balance computation.
 * See Priya's requirement: "Fix the USD/INR currency mismatch."
 */
public enum Currency {

    /**
     * Indian Rupee — the base currency for all balance calculations.
     * Symbol: ₹
     */
    INR("Indian Rupee", "₹"),

    /**
     * US Dollar — used for trip expenses (Dev's entries).
     * Symbol: $
     * Converted to INR at a static rate of 83.00 (see V6 migration).
     */
    USD("US Dollar", "$");

    private final String displayName;
    private final String symbol;

    Currency(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }
}
