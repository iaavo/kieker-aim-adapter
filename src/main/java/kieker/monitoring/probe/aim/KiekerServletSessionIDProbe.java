package kieker.monitoring.probe.aim;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aim.api.instrumentation.AbstractEnclosingProbe;
import org.aim.api.instrumentation.ProbeBeforePart;
import org.lpe.common.extension.IExtension;

import kieker.monitoring.core.registry.SessionRegistry;

/**
 * Probe for measuring response time in milli-seconds.
 */
public class KiekerServletSessionIDProbe extends AbstractEnclosingProbe {

	public KiekerServletSessionIDProbe(final IExtension<?> provider) {
		super(provider);
	}

	/**
	 * After part.
	 */
	@ProbeBeforePart(requiredMethodName = "doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse")
	public void beforeDoGetPart() {
		final HttpServletRequest req = ((HttpServletRequest) this.__parameter[1]);
		final HttpServletResponse resp = ((HttpServletResponse) this.__parameter[2]);
		SessionRegistry.INSTANCE.storeThreadLocalSessionId(req.getRequestedSessionId());
	}

}
