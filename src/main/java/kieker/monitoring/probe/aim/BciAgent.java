package kieker.monitoring.probe.aim;

import java.lang.instrument.Instrumentation;

import org.aim.mainagent.instrumentation.JInstrumentation;

import kieker.monitoring.core.controller.MonitoringController;

public class BciAgent {
	/**
	 * Main method for the agent. Initializes the agent. This method is called,
	 * when the agent is started with a java application as argument.
	 *
	 * @param agentArgs
	 *            arguments for the agent. Not used currently.
	 * @param inst
	 *            Java instrumentation instance
	 */
	public static void premain(final String agentArgs, final Instrumentation inst) {
		BciAgent.agentmain(agentArgs, inst);
	}

	/**
	 * Main method for the agent. Initializes the agent. This method is called,
	 * when the agent is loaded into a JVM at runtime.
	 *
	 * @param agentArgs
	 *            arguments for the agent. Not used currently.
	 * @param inst
	 *            Java instrumentation instance
	 */
	public static void agentmain(final String agentArgs, final Instrumentation inst) {
		try {
			if (!inst.isRedefineClassesSupported()) {
				throw new IllegalStateException(
						"Redefining classes not supported, InstrumentationAgent cannot work properly!");
			}
			JInstrumentation.getInstance().setjInstrumentation(inst);
			MonitoringController.getInstance();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
}
