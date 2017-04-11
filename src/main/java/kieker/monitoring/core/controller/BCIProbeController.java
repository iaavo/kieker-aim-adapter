/***************************************************************************
 * Copyright 2014 Kieker Project (http://kieker-monitoring.net)
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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.api.instrumentation.AbstractEnclosingProbe;

import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.monitoring.core.signaturePattern.InvalidPatternException;
import kieker.monitoring.core.signaturePattern.PatternEntry;
import kieker.monitoring.core.signaturePattern.PatternParser;
import kieker.monitoring.probe.aim.AimOperationExecutionProbe;

public class BCIProbeController implements IProbeController {
	public static BCIProbeController instance = new BCIProbeController();
	
	static final Log LOG = LogFactory.getLog(BCIProbeController.class); // NOPMD
																		// package
																		// for
																		// inner
																		// class
	
	Class<? extends AbstractEnclosingProbe> bciProbe;

	private final ConcurrentMap<String, Boolean> signatureCache = new ConcurrentHashMap<String, Boolean>();
	final List<PatternEntry> patternList = new ArrayList<PatternEntry>(); // only
																		  // accessed
																		  // synchronized

	final KiekerInstrumentor instrumentor = new KiekerInstrumentor();

	private final ClassLoadingListener classLoaderListener = new ClassLoadingListener() {
		public void onLoadClass(final Class<?> clazz) {
			try {
				BCIProbeController.this.instrumentor.reinstrument(clazz, BCIProbeController.this.patternList,
						BCIProbeController.this.bciProbe);
			} catch (final InstrumentationException e) {
				LOG.error("Class '" + clazz.getName() + "' has not been instrumented correctly.", e);
			}
		}
	};

	protected BCIProbeController() {
		this.bciProbe = AimOperationExecutionProbe.class;
		this.addClassLoaderListener();
	}

	protected BCIProbeController(final String bciProbeClassName) {
		this.bciProbe = this.getProbeFromString(bciProbeClassName, AimOperationExecutionProbe.class);
		this.addClassLoaderListener();
	}

	private void addClassLoaderListener() {
		final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
		if (classLoader instanceof KiekerClassLoader) {
			((KiekerClassLoader) classLoader).setListener(this.classLoaderListener);
		}
	}

	@SuppressWarnings("unchecked")
	private Class<? extends AbstractEnclosingProbe> getProbeFromString(final String className,
			final Class<? extends AbstractEnclosingProbe> defaultClass) {
		try {
			final Class<?> configClass = Class.forName(className);
			if (AbstractEnclosingProbe.class.isAssignableFrom(configClass)) {
				return ((Class<? extends AbstractEnclosingProbe>) configClass);
			}
		} catch (final ClassNotFoundException e) {
			LOG.warn("Class for BCI-Probe not found: '" + e.getMessage() + "' Resorting to default Probe.");
		}
		return defaultClass;
	}

	protected void cleanup() {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Shutting down Probe Controller");
		}
	}

	private boolean addProbe(final String strPattern, final boolean activated) {
		synchronized (this) {
			final Pattern pattern;
			try {
				pattern = PatternParser.parseToPattern(strPattern);
			} catch (final InvalidPatternException ex) {
				LOG.error("'" + strPattern + "' is not a valid pattern.", ex);
				return false;
			}
			final PatternEntry patternEntry = new PatternEntry(strPattern, pattern, activated);
			this.addPattern(patternEntry);

			this.reinstrument();
		}
		return true;
	}

	private synchronized void reinstrument() {
		try {
			this.instrumentor.reinstrument(this.patternList, this.bciProbe);
		} catch (final Exception e2) {
			LOG.error("Has not been instrumented correctly.", e2);
		}

	}

	public boolean activateProbe(final String strPattern) {
		return this.addProbe(strPattern, true);
	}

	public boolean deactivateProbe(final String strPattern) {
		return this.addProbe(strPattern, false);
	}

	public boolean isProbeActivated(final String signature) {
		final Boolean active = this.signatureCache.get(signature);
		if (null == active) {
			return this.matchesPattern(signature);
		} else {
			return active;
		}
	}

	protected void setProbePatternList(final List<String> strPatternList, final boolean updateConfig) {
		synchronized (this) {
			this.patternList.clear();
			this.signatureCache.clear();
			for (final String string : strPatternList) {
				if (string.length() > 0) { // ignore empty lines
					try {
						switch (string.charAt(0)) {
						case '+':
							this.patternList.add(new PatternEntry(string.substring(1).trim(), true));
							break;
						case '-':
							this.patternList.add(new PatternEntry(string.substring(1).trim(), false));
							break;
						case '#':
							// ignore comment
							break;
						default:
							LOG.warn("Each line should either start with '+', '-', or '#'. Ignoring: " + string);
							break;
						}
					} catch (final InvalidPatternException ex) {
						LOG.error("'" + string.substring(1) + "' is not a valid pattern.", ex);
					}
				}
			}
			try {
				this.instrumentor.undoInstrumentation();
				this.reinstrument();
			} catch (final InstrumentationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public void setProbePatternList(final List<String> strPatternList) {
		this.setProbePatternList(strPatternList, true);
	}

	public List<String> getProbePatternList() {
		synchronized (this) {
			final List<String> list = new ArrayList<String>(this.patternList.size());
			for (final PatternEntry entry : this.patternList) {
				final String strPattern;
				if (entry.isActivated()) {
					strPattern = '+' + entry.getStrPattern();
				} else {
					strPattern = '-' + entry.getStrPattern();
				}
				list.add(strPattern);
			}
			return list;
		}
	}

	private boolean matchesPattern(final String signature) {
		synchronized (this) {
			final ListIterator<PatternEntry> patternListIterator = this.patternList
					.listIterator(this.patternList.size());
			while (patternListIterator.hasPrevious()) {
				final PatternEntry patternEntry = patternListIterator.previous();
				if (patternEntry.getPattern().matcher(signature).matches()) {
					final boolean value = patternEntry.isActivated();
					this.signatureCache.put(signature, value);
					return value;
				}
			}
		}
		// Do not forget to remember this default value
		this.signatureCache.put(signature, true);

		return true; // if nothing matches, the default is true!
	}

	private synchronized void addPattern(final PatternEntry patternEntry) {
		// we must always clear the cache!
		this.signatureCache.clear();
		this.patternList.add(patternEntry);
	}

	public void enableMonitoring() {
		try {
			this.instrumentor.reinstrument(this.patternList, this.bciProbe);
		} catch (final InstrumentationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void disableMonitoring() {
		try {
			this.instrumentor.undoInstrumentation();
		} catch (final InstrumentationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
