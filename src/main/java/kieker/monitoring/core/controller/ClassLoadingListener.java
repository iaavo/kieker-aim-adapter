package kieker.monitoring.core.controller;

public interface ClassLoadingListener {
	public void onLoadClass(final Class<?> clazz);
}
