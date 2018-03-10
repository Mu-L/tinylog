/*
 * Copyright 2018 Martin Winandy
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.tinylog.policies;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.tinylog.configuration.ServiceLoader;
import org.tinylog.util.FileSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests for {@link DailyPolicy}.
 */
@RunWith(Enclosed.class)
public final class DailyPolicyTest {

	/**
	 * Converts a local date and time to epoch milliseconds.
	 * 
	 * @param date
	 *            Local date
	 * @param time
	 *            Local time
	 * @return Milliseconds since 1970-01-01T00:00:00Z
	 */
	private static long asEpochMilliseconds(final LocalDate date, final LocalTime time) {
		return ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	/**
	 * Tests for daily policy with default time (00:00).
	 */
	@RunWith(PowerMockRunner.class)
	@PrepareForTest(DailyPolicy.class)
	public static final class DefaultTimeTest {

		/**
		 * Initialize mocking of {@link System} and {@link Calendar}.
		 */
		@Before
		public void init() {
			mockStatic(System.class, Calendar.class);
		}

		/**
		 * Verifies that an already existing file with the current time as last modification date will be continued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileWithCurrentTime() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(System.currentTimeMillis());

			DailyPolicy policy = new DailyPolicy(null);
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification at midnight will be continued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileFromMidnight() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 3), LocalTime.of(0, 0)));

			DailyPolicy policy = new DailyPolicy(null);
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification at the last day will be discontinued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void discontinueExistingFileFromLastDay() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 2), LocalTime.of(23, 59)));

			DailyPolicy policy = new DailyPolicy(null);
			assertThat(policy.continueExistingFile(path)).isFalse();
		}

		/**
		 * Verifies that the current file will be continued immediately after start.
		 */
		@Test
		public void continueCurrentFileAfterStart() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy(null);

			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be still continued one minute before the expected rollover event.
		 */
		@Test
		public void continueCurrentFileOneMinuteBeforeRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy(null);

			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(23, 59));
			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be discontinued at the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileAtRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy(null);

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(0, 0));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Verifies that the current file will be still discontinued one minute after the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileOneMinuteAfterRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy(null);

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(0, 1));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Sets the current date and time.
		 * 
		 * @param date
		 *            New current date
		 * @param time
		 *            New current time
		 */
		private static void setTime(final LocalDate date, final LocalTime time) {
			long milliseconds = asEpochMilliseconds(date, time);

			when(System.currentTimeMillis()).thenReturn(milliseconds);
			when(Calendar.getInstance()).then(new CalendarAnswer(milliseconds));
		}

	}

	/**
	 * Tests for daily policy with custom time that contains only an hour (6 a.m.).
	 */
	@RunWith(PowerMockRunner.class)
	@PrepareForTest(DailyPolicy.class)
	public static final class CustomHourOnlyTimeTest {

		/**
		 * Initialize mocking of {@link System} and {@link Calendar}.
		 */
		@Before
		public void init() {
			mockStatic(System.class, Calendar.class);
		}

		/**
		 * Verifies that an already existing file with the current time as last modification date will be continued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileWithCurrentTime() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(System.currentTimeMillis());

			DailyPolicy policy = new DailyPolicy("6");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification from last rollover at 6 a.m. at the same day
		 * will be continued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileFromSameDay() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 3), LocalTime.of(6, 0)));

			DailyPolicy policy = new DailyPolicy("6");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification at 5:69 a.m. at the same day will be
		 * discontinued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void discontinueExistingFileFromSameDay() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 3), LocalTime.of(5, 59)));

			DailyPolicy policy = new DailyPolicy("6");
			assertThat(policy.continueExistingFile(path)).isFalse();
		}

		/**
		 * Verifies that the current file will be continued immediately after start.
		 */
		@Test
		public void continueCurrentFileAfterStart() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("6");

			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be still continued one minute before the expected rollover event.
		 */
		@Test
		public void continueCurrentFileOneMinuteBeforeRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("6");

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(5, 59));
			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be discontinued at the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileAtRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("6");

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(6, 0));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Verifies that the current file will be still discontinued one minute after the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileOneMinuteAfterRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("6");

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(6, 1));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Sets the current date and time.
		 * 
		 * @param date
		 *            New current date
		 * @param time
		 *            New current time
		 */
		private static void setTime(final LocalDate date, final LocalTime time) {
			long milliseconds = asEpochMilliseconds(date, time);

			when(System.currentTimeMillis()).thenReturn(milliseconds);
			when(Calendar.getInstance()).then(new CalendarAnswer(milliseconds));
		}

	}

	/**
	 * Tests for daily policy with custom time that contains an hour and minutes (01:30).
	 */
	@RunWith(PowerMockRunner.class)
	@PrepareForTest(DailyPolicy.class)
	public static final class CustomFullTimeTest {

		/**
		 * Initialize mocking of {@link System} and {@link Calendar}.
		 */
		@Before
		public void init() {
			mockStatic(System.class, Calendar.class);
		}

		/**
		 * Verifies that an already existing file with the current time as last modification date will be continued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileWithCurrentTime() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(System.currentTimeMillis());

			DailyPolicy policy = new DailyPolicy("01:30");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification from last rollover at 01:30 at the same day
		 * will be continued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileFromSameDay() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 3), LocalTime.of(1, 30)));

			DailyPolicy policy = new DailyPolicy("01:30");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification at 01:29 at the same day will be discontinued.
		 * 
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void discontinueExistingFileFromSameDay() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 3), LocalTime.of(1, 29)));

			DailyPolicy policy = new DailyPolicy("01:30");
			assertThat(policy.continueExistingFile(path)).isFalse();
		}

		/**
		 * Verifies that the current file will be continued immediately after start.
		 */
		@Test
		public void continueCurrentFileAfterStart() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("01:30");

			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be still continued one minute before the expected rollover event.
		 */
		@Test
		public void continueCurrentFileOneMinuteBeforeRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("01:30");

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(1, 29));
			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be discontinued at the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileAtRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("01:30");

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(1, 30));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Verifies that the current file will be still discontinued one minute after the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileOneMinuteAfterRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			DailyPolicy policy = new DailyPolicy("01:30");

			setTime(LocalDate.of(1985, 6, 4), LocalTime.of(1, 31));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Sets the current date and time.
		 * 
		 * @param date
		 *            New current date
		 * @param time
		 *            New current time
		 */
		private static void setTime(final LocalDate date, final LocalTime time) {
			long milliseconds = asEpochMilliseconds(date, time);

			when(System.currentTimeMillis()).thenReturn(milliseconds);
			when(Calendar.getInstance()).then(new CalendarAnswer(milliseconds));
		}

	}

	/**
	 * Tests for service registration.
	 */
	public static final class ServiceRegistrationTest {

		/**
		 * Verifies that policy is registered as service under the name "daily".
		 */
		@Test
		public void isRegistered() {
			Policy policy = new ServiceLoader<>(Policy.class, String.class).create("daily", (String) null);
			assertThat(policy).isInstanceOf(DailyPolicy.class);
		}

	}

	/**
	 * Answer for mocked calendars.
	 */
	private static final class CalendarAnswer implements Answer<Calendar> {

		private final long milliseconds;

		/**
		 * @param milliseconds
		 *            Milliseconds since 1970-01-01T00:00:00Z
		 */
		private CalendarAnswer(final long milliseconds) {
			this.milliseconds = milliseconds;
		}

		@Override
		public Calendar answer(final InvocationOnMock invocation) throws Throwable {
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(milliseconds);
			return calendar;
		}

	}

}
