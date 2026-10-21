package com.splitmate.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Money helpers. Amounts are always {@code long} minor units (cents); doubles never touch money.
 *
 * <p>{@link #parse(String)} accepts the formats people actually type, including European ones:
 * {@code "86.4"}, {@code "86,40"}, {@code "1.234,50"}, {@code "1,234.50"}, {@code "€ 12"}.
 */
public final class Money {

	/** Largest single amount accepted: 100,000.00 in major units. */
	public static final long MAX_AMOUNT_MINOR = 10_000_000L;

	private static final Pattern GROUPED_DOT = Pattern.compile("^[1-9]\\d{0,2}(\\.\\d{3})+$");
	private static final Pattern GROUPED_COMMA = Pattern.compile("^[1-9]\\d{0,2}(,\\d{3})+$");

	private Money() {
	}

	/**
	 * Parses user input into minor units.
	 * @throws ApiException 400 when the input is not a positive amount with at most two decimals
	 */
	public static long parse(String input) {
		if (input == null || input.isBlank()) {
			throw ApiException.badRequest("Enter an amount");
		}
		String s = input.trim().replaceAll("[\\s '€$£₹]", "");
		if (s.startsWith("+")) {
			s = s.substring(1);
		}
		if (s.startsWith("-")) {
			throw ApiException.badRequest("Amount must be greater than zero");
		}
		if (!s.matches("[0-9.,]+")) {
			throw ApiException.badRequest("'" + input.trim() + "' is not a valid amount");
		}
		String normalized = normalizeSeparators(s, input);
		BigDecimal value;
		try {
			value = new BigDecimal(normalized);
		}
		catch (NumberFormatException ex) {
			throw ApiException.badRequest("'" + input.trim() + "' is not a valid amount");
		}
		if (value.scale() > 2) {
			throw ApiException.badRequest("Amounts can have at most 2 decimal places");
		}
		if (value.signum() <= 0) {
			throw ApiException.badRequest("Amount must be greater than zero");
		}
		long minor = value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
		if (minor > MAX_AMOUNT_MINOR) {
			throw ApiException.badRequest("Amount cannot be more than " + format(MAX_AMOUNT_MINOR, "EUR"));
		}
		return minor;
	}

	/**
	 * Decides which character is the decimal separator. The last separator wins when both appear
	 * ("1.234,50" and "1,234.50"); a lone separator followed by exactly three digits is a thousands
	 * separator ("1.234" = 1234, "1,234" = 1234), otherwise it is the decimal point.
	 */
	private static String normalizeSeparators(String s, String original) {
		int lastDot = s.lastIndexOf('.');
		int lastComma = s.lastIndexOf(',');
		if (lastDot >= 0 && lastComma >= 0) {
			char decimal = lastDot > lastComma ? '.' : ',';
			char group = decimal == '.' ? ',' : '.';
			int decimalIndex = s.lastIndexOf(decimal);
			String intPart = s.substring(0, decimalIndex);
			String fraction = s.substring(decimalIndex + 1);
			boolean validGrouping = (group == '.' ? GROUPED_DOT : GROUPED_COMMA).matcher(intPart).matches();
			if (!validGrouping || fraction.isEmpty()) {
				throw ApiException.badRequest("'" + original.trim() + "' is not a valid amount");
			}
			return intPart.replace(String.valueOf(group), "") + "." + fraction;
		}
		char sep = lastDot >= 0 ? '.' : (lastComma >= 0 ? ',' : 0);
		if (sep == 0) {
			return s;
		}
		boolean grouped = (sep == '.' ? GROUPED_DOT : GROUPED_COMMA).matcher(s).matches();
		if (grouped) {
			return s.replace(String.valueOf(sep), "");
		}
		if (s.indexOf(sep) != s.lastIndexOf(sep)) {
			throw ApiException.badRequest("'" + original.trim() + "' is not a valid amount");
		}
		return s.replace(sep, '.');
	}

	/** Formats minor units for display, e.g. {@code 8640, "EUR"} → {@code "€86.40"}. */
	public static String format(long minor, String currencyCode) {
		NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.UK);
		Currency currency = Currency.getInstance(currencyCode);
		nf.setCurrency(currency);
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
		return nf.format(BigDecimal.valueOf(minor, 2));
	}

	/** Formats with an explicit sign: {@code +€12.00} / {@code −€12.00} (true minus sign). */
	public static String formatSigned(long minor, String currencyCode) {
		if (minor == 0) {
			return format(0, currencyCode);
		}
		return (minor > 0 ? "+" : "−") + format(Math.abs(minor), currencyCode);
	}

	/** Plain decimal string without currency symbol, e.g. {@code 8640} → {@code "86.40"} (used in CSV). */
	public static String toDecimalString(long minor) {
		return BigDecimal.valueOf(minor, 2).toPlainString();
	}

}
