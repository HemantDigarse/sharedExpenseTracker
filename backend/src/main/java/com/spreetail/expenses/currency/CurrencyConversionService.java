package com.spreetail.expenses.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service interface for currency conversion operations.
 *
 * <p>Abstracted as an interface for testability — unit tests can
 * provide a mock implementation with known conversion rates instead
 * of depending on database state.
 *
 * <p>The primary implementation is {@link CurrencyConversionServiceImpl}
 * which reads rates from the {@code exchange_rates} database table.
 *
 * @see CurrencyConversionServiceImpl
 */
public interface CurrencyConversionService {

    /**
     * Converts an amount from one currency to another using the
     * exchange rate effective on the given date.
     *
     * <p>If fromCurrency equals toCurrency, returns the amount unchanged.
     *
     * <p>Rounding rule: BigDecimal HALF_UP to 2 decimal places.
     * This means values >= 0.005 round up, values < 0.005 round down.
     * Example: 4150.005 → 4150.01, 4150.004 → 4150.00
     *
     * @param amount       the amount to convert (must not be null)
     * @param fromCurrency the source currency
     * @param toCurrency   the target currency
     * @param date         the date for which to look up the exchange rate
     * @return the converted amount in the target currency, rounded to 2 decimal places
     * @throws com.spreetail.expenses.common.ResourceNotFoundException
     *         if no exchange rate is found for the currency pair on or before the given date
     */
    BigDecimal convert(BigDecimal amount, Currency fromCurrency, Currency toCurrency, LocalDate date);

    /**
     * Gets the exchange rate for a currency pair on a given date.
     *
     * <p>Looks up the most recent rate with effective_date <= the given date.
     * This means a rate set on Jan 1 applies to all subsequent dates
     * until a newer rate is inserted.
     *
     * @param fromCurrency source currency
     * @param toCurrency   target currency
     * @param date         the date to find the rate for
     * @return the exchange rate (e.g., 83.00 for USD→INR)
     * @throws com.spreetail.expenses.common.ResourceNotFoundException
     *         if no rate is found
     */
    BigDecimal getRate(Currency fromCurrency, Currency toCurrency, LocalDate date);
}
