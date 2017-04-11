/***************************************************************************
 * Copyright 2015 Kieker Project (http://kieker-monitoring.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/

package kieker.monitoring.core.controller;

/**
 * @author Albert Flaig
 */
public interface IInstrumentationController {

	/**
	 * Instruments a probe by using Byte Code Injection and hotswapping.
	 *
	 * @param pattern
	 *            pattern for the probe
	 * @return
	 * 		true on success
	 */
	public boolean instrumentProbe(final String pattern);

	/**
	 * Restores a class in its original form, removing all instrumentations.
	 *
	 * @param pattern
	 *            pattern for the probe
	 * @return
	 * 		true on success
	 */
	public boolean restoreClass(final String pattern);

	/**
	 * Tests if a probe is instrumented.
	 *
	 * This test is ignorant of the fact whether monitoring itself is enabled/disabled/terminated.
	 *
	 * @param signature
	 *            signature of the probe
	 * @return
	 * 		true if the probe with this signature is instrumented
	 */
	public boolean isInstrumented(final String signature);
}
