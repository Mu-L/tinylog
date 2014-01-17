/*
 * Copyright 2012 Martin Winandy
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

package org.pmw.tinylog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.pmw.tinylog.labellers.TimestampLabeller;
import org.pmw.tinylog.policies.SizePolicy;
import org.pmw.tinylog.util.FileHelper;
import org.pmw.tinylog.util.LogEntryBuilder;
import org.pmw.tinylog.util.StoreWriter;
import org.pmw.tinylog.writers.LogEntry;
import org.pmw.tinylog.writers.LogEntryValue;
import org.pmw.tinylog.writers.RollingFileWriter;

/**
 * Tests old fixed bugs to prevent regressions.
 */
public class RegressionsTest extends AbstractTest {

	/**
	 * Bug: Wrong class in log entry if there isn't set any special logging level for at least one package.
	 */
	@Test
	public final void testWrongClass() {
		StoreWriter writer = new StoreWriter(LogEntryValue.LOGGING_LEVEL, LogEntryValue.CLASS);
		Configurator.defaultConfig().writer(writer).level(LoggingLevel.TRACE).activate();

		Configurator.currentConfig().level("org", LoggingLevel.TRACE).activate();
		Logger.info("");
		LogEntry logEntry = writer.consumeLogEntry();
		assertEquals(LoggingLevel.INFO, logEntry.getLoggingLevel());
		assertEquals(RegressionsTest.class.getName(), logEntry.getClassName()); // Was already OK

		Configurator.currentConfig().level("org", null).activate();
		Logger.info("");
		logEntry = writer.consumeLogEntry();
		assertEquals(LoggingLevel.INFO, logEntry.getLoggingLevel());
		assertEquals(RegressionsTest.class.getName(), logEntry.getClassName()); // Failed
	}

	/**
	 * Bug: If a log file is continued, the policy will start from scratch. This leads to a too late rollover.
	 * 
	 * @throws Exception
	 *             Test failed
	 */
	@Test
	public final void testContinueLogFile() throws Exception {
		File file = FileHelper.createTemporaryFile("tmp");

		RollingFileWriter writer = new RollingFileWriter(file.getAbsolutePath(), 0, new SizePolicy(10));
		writer.init();
		writer.write(new LogEntryBuilder().renderedLogEntry("12345").create());
		writer.close();

		writer = new RollingFileWriter(file.getAbsolutePath(), 0, new SizePolicy(10));
		writer.init();
		writer.write(new LogEntryBuilder().renderedLogEntry("123456").create());
		writer.close();

		assertEquals(6, file.length());
		file.delete();
	}

	/**
	 * Bug: IllegalArgumentException if there are curly brackets in the log message.
	 */
	@Test
	public final void testCurlyBracketsInText() {
		StoreWriter writer = new StoreWriter();
		Configurator.defaultConfig().writer(writer).activate();

		Logger.info("{TEST}"); // Failed (java.lang.IllegalArgumentException)

		LogEntry logEntry = writer.consumeLogEntry();
		assertEquals(LoggingLevel.INFO, logEntry.getLoggingLevel());
		assertEquals("{TEST}", logEntry.getMessage());
	}

	/**
	 * Bug: Logging writer gets active logging level instead of the logging level of the log entry.
	 */
	@Test
	public final void testLoggingLevel() {
		StoreWriter writer = new StoreWriter();
		Configurator.defaultConfig().writer(writer).level(LoggingLevel.INFO).activate();

		Logger.error("Hello");

		LogEntry logEntry = writer.consumeLogEntry();
		assertEquals(LoggingLevel.ERROR, logEntry.getLoggingLevel());
		assertEquals("Hello", logEntry.getMessage());
	}

	/**
	 * Bug: If all custom logging levels for packages are lower than the default package level, the custom logging
	 * levels will be ignored.
	 */
	@Test
	public final void testLowerCustomLoggingLevelsForPackages() {
		StoreWriter writer = new StoreWriter();
		Configurator.defaultConfig().level(LoggingLevel.INFO).level(RegressionsTest.class.getPackage().getName(), LoggingLevel.OFF).activate();
		Logger.info("should be ignored"); // Was output
		assertNull(writer.consumeLogEntry());
	}

	/**
	 * Bug: Timestamps need a locale but the locale isn't set at startup.
	 * 
	 * @throws Exception
	 *             Test failed
	 */
	@Test
	public final void testTimestampLabellerAtStartup() throws Exception {
		Logger.setConfirguration(null);
		new RollingFileWriter(FileHelper.createTemporaryFile("txt").getName(), 0, new TimestampLabeller()); // Failed
	}

	/**
	 * Bug: Rolling fails for files without a parent path in timestamp labeller.
	 * 
	 * @throws IOException
	 *             Test failed
	 */
	@Test
	public final void testTimestampLabellerRolling() throws IOException {
		File file = FileHelper.createTemporaryFileInWorkspace("log");
		file = new File(file.getName());
		assertTrue(file.exists());
		TimestampLabeller labeller = new TimestampLabeller();
		file = labeller.getLogFile(file);
		labeller.roll(file, 10); // Failed
	}

}
