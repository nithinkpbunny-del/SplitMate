package com.splitmate.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(delimiter = '|', value = {
			"86.4|8640",
			"86,40|8640",
			"86.40|8640",
			"86|8600",
			"0.01|1",
			"1.234,50|123450",
			"1,234.50|123450",
			"1.234|123400",
			"1,234|123400",
			"12.345.678|",
			"€ 12,5|1250",
			"  7 |700",
			"+3.10|310",
			"1 234,56|123456" })
	void parsesCommonFormats(String input, Long expected) {
		if (expected == null) {
			assertThatThrownBy(() -> Money.parse(input)).isInstanceOf(ApiException.class);
		}
		else {
			assertThat(Money.parse(input)).isEqualTo(expected);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0,00", "-5", "1.234.5", "abc", "1,2,3", "", "  ", "86,4051", "0.505", "1.2.3,4" })
	void rejectsInvalidInput(String input) {
		assertThatThrownBy(() -> Money.parse(input)).isInstanceOf(ApiException.class);
	}

	@Test
	void rejectsMoreThanTwoDecimals() {
		assertThatThrownBy(() -> Money.parse("86.4051")).hasMessageContaining("2 decimal places");
		assertThatThrownBy(() -> Money.parse("1.234,567")).hasMessageContaining("2 decimal places");
		// a lone separator followed by exactly three digits is read as thousands grouping (German "1.234")
		assertThat(Money.parse("86.405")).isEqualTo(8_640_500);
	}

	@Test
	void enforcesMaximum() {
		assertThat(Money.parse("100000")).isEqualTo(Money.MAX_AMOUNT_MINOR);
		assertThatThrownBy(() -> Money.parse("100000.01")).hasMessageContaining("cannot be more than");
	}

	@Test
	void formats() {
		assertThat(Money.format(8640, "EUR")).isEqualTo("€86.40");
		assertThat(Money.format(123456789, "EUR")).isEqualTo("€1,234,567.89");
		assertThat(Money.formatSigned(-1204, "EUR")).isEqualTo("−€12.04");
		assertThat(Money.formatSigned(1204, "EUR")).isEqualTo("+€12.04");
		assertThat(Money.toDecimalString(5)).isEqualTo("0.05");
	}

}
