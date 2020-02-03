package com.zhongyou.util.format;

import com.zhongyou.util.collection.ArrayMap;
import com.zhongyou.util.ref.BiRef;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class TimeFormat {
	private static final Map<Locale, List<BiRef<Integer, String>>> sDurationFormatParam = new ArrayMap<>();
	private static final Map<Locale, String> sDateFormatParam = new ArrayMap<>();

	static {
		setupDurationFormatParam();
		setupDateFormatParam();
	}

	private static void setupDurationFormatParam() {
		sDurationFormatParam.put(Locale.US, Arrays.asList(
				BiRef.create(60, "%2s\""),
				BiRef.create(60, "%2s\'"),
				BiRef.create(24, "%2sh"),
				BiRef.create(null, "%2sd")
		));
		sDurationFormatParam.put(Locale.CHINA, Arrays.asList(
				BiRef.create(60, "%2s秒"),
				BiRef.create(60, "%2s分"),
				BiRef.create(24, "%2s时"),
				BiRef.create(null, "%2s天")
		));
	}

	private static void setupDateFormatParam() {
		sDateFormatParam.put(Locale.US, "yyyy-MM-dd HH:mm:ss");
		sDateFormatParam.put(Locale.CHINA, "yyyy-MM-dd HH:mm:ss");
	}

	private TimeFormat() {

	}

	public static String formatDuration(long durationMilliSeconds, Locale locale) {
		List<BiRef<Integer, String>> formatParam = sDurationFormatParam.get(locale);
		if (formatParam == null) {
			formatParam = sDurationFormatParam.get(Locale.US);
		}
		long duration = durationMilliSeconds / 1000;
		Stack<String> stack = new Stack<>();
		for (BiRef<Integer, String> part : formatParam) {
			if (part.value1 == null) {
				stack.push(String.format(part.value2, duration));
				break;
			} else {
				long partValue = duration % part.value1;
				duration -= partValue;
				duration /= part.value1;
				stack.push(String.format(part.value2, partValue));
				if (duration <= 0) {
					break;
				}
			}
		}
		StringBuilder stringBuilder = new StringBuilder();
		while (!stack.isEmpty()) {
			stringBuilder.append(stack.pop());
		}
		return stringBuilder.toString();
	}

	public static String formatDuration(long durationMilliSeconds) {
		return formatDuration(durationMilliSeconds, Locale.getDefault());
	}

	public static String formatDate(Date date, Locale formatLocale, Locale timeLocale) {
		Objects.requireNonNull(date);
		Objects.requireNonNull(formatLocale);
		Objects.requireNonNull(timeLocale);
		String format = sDateFormatParam.get(formatLocale);
		if (format == null) {
			format = sDateFormatParam.get(Locale.US);
		}
		return new SimpleDateFormat(format, timeLocale).format(date);
	}

	public static String formatDate(Date date) {
		return formatDate(date, Locale.getDefault(), Locale.getDefault());
	}

}
