package kieker.monitoring.core.signaturePattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class KiekerPattern {

	private final Pattern fullPattern;
	private final Pattern fqPattern;

	public KiekerPattern(final Pattern modifierPattern, final Pattern returnTypePattern, final Pattern fqPattern, final Pattern methodNamePattern,
			final Pattern parametersPattern, final Pattern throwPattern) {
		final StringBuffer sb = new StringBuffer();
		sb.append(modifierPattern.toString());
		sb.append(returnTypePattern.toString());
		sb.append(fqPattern.toString());
		sb.append("\\.");
		sb.append(methodNamePattern.toString());
		sb.append("\\(");
		sb.append(parametersPattern.toString());
		sb.append("\\)");
		sb.append(throwPattern.toString());
		this.fullPattern = Pattern.compile(sb.toString());
		this.fqPattern = fqPattern;
	}

	public KiekerPattern(final Pattern fullPattern) {
		this.fullPattern = fullPattern;
		this.fqPattern = Pattern.compile(".*");
	}

	public boolean matchFq(final CharSequence string) {
		return this.fqPattern.matcher(string).matches();
	}

	public boolean match(final CharSequence string) {
		return this.fullPattern.matcher(string).matches();
	}

	public Matcher matcher(final CharSequence string) {
		return this.fullPattern.matcher(string);
	}

	@Override
	public String toString() {
		return this.getClass().getName() + ":" + this.fullPattern.pattern();
	}
}
