package com.spreetail.expenses.currency;

import com.spreetail.expenses.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Implementation of {@link CurrencyConversionService} that uses a
 * static USD→INR conversion rate.
 *
 * <p>DESIGN DECISION (see DECISIONS.md: "Static vs dynamic currency rate"):
 * We use a static rate instead of a live API because:
 * <ol>
 *   <li>Expense data is historical (Feb–May) — consistency matters more
 *       than real-time accuracy for retroactive reconciliation</li>
 *   <li>Eliminates external API dependency (works offline)</li>
 *   <li>The core requirement is "a dollar is not a rupee" — any reasonable
 *       conversion rate fixes Priya's problem</li>
 * </ol>
 *
 * <p>The rate is configurable via the {@code currency.usd-to-inr-rate}
 * property (default: 83.00). It can be changed without redeployment
 * by setting the {@code USD_TO_INR_RATE} environment variable.
 *
 * <p>ROUNDING RULE: All conversions use {@code BigDecimal.HALF_UP}
 * to 2 decimal places. This is the standard rounding mode for
 * financial calculations (also called "banker's rounding up").
 * Example: 4150.005 → 4150.01
 */
@Service
public class CurrencyConversionServiceImpl implements CurrencyConversionService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionServiceImpl.class);

    /**
     * Number of decimal places for monetary values.
     * All amounts are rounded to 2 decimal places (e.g., ₹1,234.56).
     */
    private static final int MONETARY_SCALE = 2;

    /**
     * Rounding mode for all monetary calculations.
     * HALF_UP: values at the midpoint (0.005) round up.
     * This is the most common rounding mode in financial applications.
     */
    private static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    /**
     * Static USD to INR conversion rate.
     * Injected from application.properties: currency.usd-to-inr-rate
     * Default: 83.00 (approximately the real rate as of early 2024)
     */
    private final BigDecimal usdToInrRate;

    /**
     * Constructor — injects the static conversion rate from config.
     *
     * @param usdToInrRate the USD→INR rate from application.properties
     */
    public CurrencyConversionServiceImpl(
            @Value("${currency.usd-to-inr-rate:83.00}") BigDecimal usdToInrRate) {
        this.usdToInrRate = usdToInrRate;
        logger.info("Currency conversion initialized with USD→INR rate: {}", usdToInrRate);
    }

    /**
     * Converts an amount between currencies.
     *
     * <p>Currently supports:
     * <ul>
     *   <li>USD → INR: multiply by usdToInrRate</li>
     *   <li>INR → USD: divide by usdToInrRate</li>
     *   <li>Same currency: return unchanged</li>
     * </ul>
     *
     * <p>The {@code date} parameter is accepted for API compatibility
     * (future support for date-specific rates) but is not currently used
     * since we have a single static rate.
     *
     * @param amount       the amount to convert
     * @param fromCurrency source currency
     * @param toCurrency   target currency
     * @param date         expense date (reserved for future date-specific rates)
     * @return converted amount, rounded to 2 decimal places using HALF_UP
     */
    @Override
    public BigDecimal convert(BigDecimal amount, Currency fromCurrency, Currency toCurrency, LocalDate date) {
        // Same currency — no conversion needed
        if (fromCurrency == toCurrency) {
            return amount.setScale(MONETARY_SCALE, MONETARY_ROUNDING);
        }

        BigDecimal rate = getRate(fromCurrency, toCurrency, date);
        BigDecimal convertedAmount = amount.multiply(rate)
                .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

        logger.debug("Converted {} {} → {} {} (rate: {}, date: {})",
                amount, fromCurrency, convertedAmount, toCurrency, rate, date);

        return convertedAmount;
    }

    /**
     * Returns the exchange rate for a given currency pair.
     *
     * <p>For USD→INR, returns the configured static rate.
     * For INR→USD, returns 1/rate (the inverse).
     *
     * @param fromCurrency source currency
     * @param toCurrency   target currency
     * @param date         expense date (reserved for future use)
     * @return the exchange rate
     * @throws ResourceNotFoundException if the currency pair is unsupported
     */
    @Override
    public BigDecimal getRate(Currency fromCurrency, Currency toCurrency, LocalDate date) {
        if (fromCurrency == toCurrency) {
            return BigDecimal.ONE;
        }

        if (fromCurrency == Currency.USD && toCurrency == Currency.INR) {
            return usdToInrRate;
        }

        if (fromCurrency == Currency.INR && toCurrency == Currency.USD) {
            // Inverse rate: 1 ÷ usdToInrRate
            // Use 6 decimal places for rate precision (matching DB NUMERIC(12,6))
            return BigDecimal.ONE.divide(usdToInrRate, 6, MONETARY_ROUNDING);
        }

        // If we reach here, the currency pair is not supported.
        // This should not happen with the current Currency enum (only INR/USD),
        // but this guard protects against future enum additions without
        // corresponding conversion logic.
        throw new ResourceNotFoundException(
                String.format("Exchange rate not found for %s → %s", fromCurrency, toCurrency)
        );
    }
}
