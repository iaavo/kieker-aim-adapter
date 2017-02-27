package kieker.monitoring.probe.aim;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aim.api.instrumentation.AbstractEnclosingProbe;
import org.aim.api.instrumentation.ProbeAfterPart;
import org.aim.api.instrumentation.ProbeBeforePart;
import org.aim.api.instrumentation.ProbeVariable;
import org.lpe.common.extension.IExtension;

import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.common.record.controlflow.OperationExecutionRecord;
import kieker.monitoring.core.controller.IMonitoringController;
import kieker.monitoring.core.controller.MonitoringController;
import kieker.monitoring.core.registry.ControlFlowRegistry;
import kieker.monitoring.core.registry.SessionRegistry;
import kieker.monitoring.probe.aspectj.operationExecution.AbstractOperationExecutionAspect;

public class AimOperationExecutionProbe extends AbstractEnclosingProbe {

	@ProbeVariable
	public long _KiekerResponsetimeProbe_startTime;
	@ProbeVariable
	public boolean _KiekerResponsetimeProbe_entryPoint;
	@ProbeVariable
	public long _KiekerResponsetimeProbe_traceId;
	@ProbeVariable
	public int _KiekerResponsetimeProbe_eoi;
	@ProbeVariable
	public int _KiekerResponsetimeProbe_ess;

	// private static final SessionRegistry SESSIONREGISTRY = SessionRegistry.INSTANCE;

	public AimOperationExecutionProbe(final IExtension<?> provider) {
		super(provider);
	}

	@ProbeBeforePart(requiredMethodName = "doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse")
	public void beforeDoGetPart() {
		final HttpServletRequest req = ((HttpServletRequest) this.__parameter[1]);
		final HttpServletResponse resp = ((HttpServletResponse) this.__parameter[2]);
		SessionRegistry.INSTANCE.storeThreadLocalSessionId(req.getRequestedSessionId());
	}

	@ProbeBeforePart()
	public void beforePart() {
		final Log log = LogFactory.getLog(AbstractOperationExecutionAspect.class);

		final IMonitoringController ctrl = MonitoringController.getInstance();
		final ControlFlowRegistry cfRegistry = ControlFlowRegistry.INSTANCE;
		// collect data
		this._KiekerResponsetimeProbe_traceId = cfRegistry.recallThreadLocalTraceId(); // traceId, -1 if entry point
		if (this._KiekerResponsetimeProbe_traceId == -1) {
			this._KiekerResponsetimeProbe_entryPoint = true;
			this._KiekerResponsetimeProbe_traceId = cfRegistry.getAndStoreUniqueThreadLocalTraceId();
			cfRegistry.storeThreadLocalEOI(0);
			cfRegistry.storeThreadLocalESS(1); // next operation is ess + 1
			this._KiekerResponsetimeProbe_eoi = 0;
			this._KiekerResponsetimeProbe_ess = 0;
		} else {
			this._KiekerResponsetimeProbe_entryPoint = false;
			this._KiekerResponsetimeProbe_eoi = cfRegistry.incrementAndRecallThreadLocalEOI(); // ess > 1
			this._KiekerResponsetimeProbe_ess = cfRegistry.recallAndIncrementThreadLocalESS(); // ess >= 0
			if ((this._KiekerResponsetimeProbe_eoi == -1) || (this._KiekerResponsetimeProbe_ess == -1)) {
				log.error("eoi and/or ess have invalid values:" + " eoi == " + this._KiekerResponsetimeProbe_eoi + " ess == " + this._KiekerResponsetimeProbe_ess);
				ctrl.terminateMonitoring();
			}
		}
		// measure before
		this._KiekerResponsetimeProbe_startTime = ctrl.getTimeSource().getTime();
	}

	/**
	 * After part.
	 */
	@ProbeAfterPart()
	public void afterPart() {
		final IMonitoringController ctrl = MonitoringController.getInstance();
		final long stopTime = ctrl.getTimeSource().getTime();
		final ControlFlowRegistry cfRegistry = ControlFlowRegistry.INSTANCE;

		final String hostname = ctrl.getHostname();
		final String sessionId = SessionRegistry.INSTANCE.recallThreadLocalSessionId();
		final OperationExecutionRecord record = new OperationExecutionRecord(
				this.__methodSignature,
				sessionId,
				this._KiekerResponsetimeProbe_traceId,
				this._KiekerResponsetimeProbe_startTime, stopTime, hostname,
				this._KiekerResponsetimeProbe_eoi,
				this._KiekerResponsetimeProbe_ess);
		ctrl.newMonitoringRecord(record);

		if (this._KiekerResponsetimeProbe_entryPoint) {
			cfRegistry.unsetThreadLocalTraceId();
			cfRegistry.unsetThreadLocalEOI();
			cfRegistry.unsetThreadLocalESS();
		} else {
			cfRegistry.storeThreadLocalESS(this._KiekerResponsetimeProbe_ess); // next operation is ess
		}
	}

}
