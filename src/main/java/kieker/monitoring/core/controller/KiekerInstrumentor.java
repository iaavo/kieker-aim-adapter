package kieker.monitoring.core.controller;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.aim.aiminterface.description.restriction.Restriction;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.api.instrumentation.AbstractEnclosingProbe;
import org.aim.api.instrumentation.description.internal.FlatInstrumentationEntity;
import org.aim.api.instrumentation.description.internal.InstrumentationSet;
import org.aim.mainagent.instrumentation.BCInjector;
import org.aim.mainagent.instrumentation.JAgentSwapper;
import org.aim.mainagent.instrumentation.JInstrumentation;

import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.monitoring.core.signaturePattern.InvalidPatternException;
import kieker.monitoring.core.signaturePattern.PatternEntry;
import kieker.monitoring.core.signaturePattern.PatternParser;
import kieker.monitoring.core.signaturePattern.SignatureFactory;

public class KiekerInstrumentor {
	private static final Log LOG = LogFactory.getLog(KiekerInstrumentor.class);
	private final Set<FlatInstrumentationEntity> currentInstrumentationState = new HashSet<FlatInstrumentationEntity>();

	public void reinstrument(final Class<?> clazz, final List<PatternEntry> patternList,
			final Class<? extends AbstractEnclosingProbe> bciProbe) throws InstrumentationException {
		// Invoked by KiekerClassLoader
		this.reinstrument(new Class<?>[] { clazz }, patternList, bciProbe);
	}

	public void reinstrument(final List<PatternEntry> patternList,
			final Class<? extends AbstractEnclosingProbe> bciProbe) throws InstrumentationException {
		// Invoked by activating probe
		final Class<?>[] classes = JInstrumentation.getInstance().getjInstrumentation().getAllLoadedClasses();
		this.reinstrument(classes, patternList, bciProbe);
	}

	private void reinstrument(final Class<?>[] classes, final List<PatternEntry> patternList,
			final Class<? extends AbstractEnclosingProbe> bciProbe) throws InstrumentationException {

		final Set<FlatInstrumentationEntity> newInstrumentationStatements = new HashSet<FlatInstrumentationEntity>();

		for (final Class<?> clazz : classes) {
			final Set<FlatInstrumentationEntity> s = this.getInstrumenationEntites(clazz, patternList, bciProbe);
			if (s != null) {
				newInstrumentationStatements.addAll(s);
			} else if (classes.length == 1) {
				// tiny heuristic
				return;
			}
		}

		final Set<Class<?>> overLappingClasses = this.revertOverlappingInstrumentation(newInstrumentationStatements);

		for (final FlatInstrumentationEntity oldEntity : this.getCurrentInstrumentationState()) {
			if (overLappingClasses.contains(oldEntity.getClazz())) {
				newInstrumentationStatements.add(oldEntity);
			}
		}

		final InstrumentationSet newInstrumentationSet = new InstrumentationSet(newInstrumentationStatements);

		if (!newInstrumentationStatements.isEmpty()) {
			final StringBuffer methodSignatures = new StringBuffer("Going to instrument the following methods:\n");
			for (final FlatInstrumentationEntity fie : newInstrumentationStatements) {
				methodSignatures.append('+');
				methodSignatures.append(fie.getMethodSignature());
				methodSignatures.append('\n');
			}
			LOG.info(methodSignatures.substring(0, methodSignatures.length() - 1));
		}

		this.injectNewInstrumentation(newInstrumentationSet);

		this.getCurrentInstrumentationState().addAll(newInstrumentationStatements);
	}

	private Set<FlatInstrumentationEntity> getInstrumenationEntites(final Class<?> clazz, List<PatternEntry> patterns,
			final Class<? extends AbstractEnclosingProbe> bciProbe) {
		patterns = this.retainPatterns(clazz.getName(), patterns);
		if (!patterns.isEmpty()) {
			final Set<FlatInstrumentationEntity> newInstrumentationStatements = new HashSet<FlatInstrumentationEntity>();
			outer: for (final Method method : clazz.getMethods()) {
				final String signature = this.getSignature(method);
				final ListIterator<PatternEntry> patternListIterator = patterns.listIterator(patterns.size());
				while (patternListIterator.hasPrevious()) {
					final PatternEntry patternEntry = patternListIterator.previous();
					if (patternEntry.getPattern().matcher(signature).matches()) {
						if (patternEntry.isActivated()) {
							final FlatInstrumentationEntity e = new FlatInstrumentationEntity(clazz, signature, 0l,
									bciProbe);
							newInstrumentationStatements.add(e);
						} else {
							continue outer;
						}
					}
				}
			}
			return newInstrumentationStatements;
		}
		return null;
	}

	private String getSignature(final Method method) {
		Class<?>[] bla = method.getParameterTypes();
		final String[] params = new String[bla.length];
		for (int i = 0; i < bla.length; i++) {
			params[i] = bla[i].getName();
		}
		bla = method.getExceptionTypes();
		final String[] exceptions = new String[bla.length];
		for (int i = 0; i < bla.length; i++) {
			exceptions[i] = bla[i].getName();
		}
		try {
			return SignatureFactory.createMethodSignature(new String[] { Modifier.toString(method.getModifiers()) },
					method.getReturnType().getName(), method.getDeclaringClass().getName(), method.getName(),
					params.length == 0 ? null : params, exceptions.length == 0 ? null : exceptions);
		} catch (final InvalidPatternException e) {
			// Should not happen
			e.printStackTrace();
		}
		return null;
	}

	private void injectNewInstrumentation(final InstrumentationSet newInstrumentationSet)
			throws InstrumentationException {
		final Map<Class<?>, byte[]> classesToRevert = BCInjector.getInstance()
				.injectInstrumentationProbes(newInstrumentationSet, Restriction.EMPTY_RESTRICTION);
		JAgentSwapper.getInstance().redefineClasses(classesToRevert);
	}

	private Set<Class<?>> revertOverlappingInstrumentation(
			final Set<FlatInstrumentationEntity> newInstrumentationStatements) throws InstrumentationException {
		final Set<Class<?>> intersection = new InstrumentationSet(newInstrumentationStatements).classesToInstrument();

		intersection.retainAll(new InstrumentationSet(this.getCurrentInstrumentationState()).classesToInstrument());
		final Map<Class<?>, byte[]> classesToRevert = BCInjector.getInstance()
				.partlyRevertInstrumentation(intersection);
		JAgentSwapper.getInstance().redefineClasses(classesToRevert);
		return intersection;
	}

	public void undoInstrumentation() throws InstrumentationException {
		final Map<Class<?>, byte[]> classesToRevert = BCInjector.getInstance().revertInstrumentation();
		JAgentSwapper.getInstance().redefineClasses(classesToRevert);
		this.getCurrentInstrumentationState().clear();
	}

	/**
	 * @return the currentInstrumentationState
	 */
	public Set<FlatInstrumentationEntity> getCurrentInstrumentationState() {
		return this.currentInstrumentationState;
	}

	private List<PatternEntry> retainPatterns(final String className, final List<PatternEntry> patternList) {
		final List<PatternEntry> matchingPatterns = new ArrayList<PatternEntry>();
		final ListIterator<PatternEntry> patternListIterator = patternList.listIterator(patternList.size());
		while (patternListIterator.hasPrevious()) {
			final PatternEntry patternEntry = patternListIterator.previous();
			final String fqClassName = extractClassNameFromPattern(patternEntry.getStrPattern());
			final Pattern fqClassPattern = parseToFqClassNamePattern(fqClassName);
			if (fqClassPattern.matcher(className).matches()) {
				matchingPatterns.add(0, patternEntry);
			}
		}
		return matchingPatterns;
	}

	private String extractClassNameFromPattern(String pattern) {
		int lastDotIndex = pattern.lastIndexOf('.');
		pattern = pattern.substring(0, lastDotIndex);
		int lastSpaceIndex = pattern.lastIndexOf(' ');

		pattern = pattern.substring(lastSpaceIndex + 1, pattern.length());
		return pattern;
	}

	private Pattern parseToFqClassNamePattern(String pattern) {
		try {
			// TODO possibility to call the method without reflection
			Method method = PatternParser.class.getDeclaredMethod("parseFQType", String.class);
			method.setAccessible(true);
			String patternStr = (String) method.invoke(null, pattern);
			return Pattern.compile(patternStr);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}

}
