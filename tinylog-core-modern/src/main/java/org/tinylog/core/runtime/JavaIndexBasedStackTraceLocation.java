/*
 * Copyright 2020 Martin Winandy
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

package org.tinylog.core.runtime;

import java.util.Optional;

import org.tinylog.core.internal.InternalLogger;

/**
 * Stack trace location implementation for modern Java 9 and later that stores the location of a callee as numeric
 * index.
 */
public class JavaIndexBasedStackTraceLocation implements StackTraceLocation {

	private final int index;

	/**
	 * @param index The index of the callee in the stack trace
	 */
	JavaIndexBasedStackTraceLocation(int index) {
		this.index = index;
	}

	@Override
	public JavaIndexBasedStackTraceLocation push() {
		return new JavaIndexBasedStackTraceLocation(index + 1);
	}

	@Override
	public String getCallerClassName() {
		String className = StackWalker.getInstance()
			.walk(stream -> index < 0 ? Optional.<StackWalker.StackFrame>empty() : stream.skip(index).findFirst())
			.map(StackWalker.StackFrame::getClassName)
			.orElse(null);

		if (className == null) {
			InternalLogger.error(null, "There is no class name at the stack trace depth of {}", index);
		}

		return className;
	}

	@Override
	public StackTraceElement getCallerStackTraceElement() {
		StackTraceElement element = StackWalker.getInstance()
			.walk(stream -> index < 0 ? Optional.<StackWalker.StackFrame>empty() : stream.skip(index).findFirst())
			.map(StackWalker.StackFrame::toStackTraceElement)
			.orElse(null);

		if (element == null) {
			InternalLogger.error(null, "There is no stack trace element at the depth of {}", index);
		}

		return element;
	}

}
