package kieker.monitoring.core.controller;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class KiekerClassLoader extends ClassLoader {
	public static Set<Class<?>> loadedClasses = Collections.synchronizedSet(new HashSet<Class<?>>());

	private ClassLoadingListener listener;

	public KiekerClassLoader(final ClassLoader parent) {
		super(parent);
	}

	@Override
	public Class<?> loadClass(final String name, final boolean resolve)
			throws ClassNotFoundException {
		if (this.isInstrumentedClass(name)) {
			Class<?> clazz = this.findLoadedClass(name);
			if (clazz == null) {
				clazz = this.findClass(name);
			}
			if (clazz == null) {
				return super.loadClass(name, resolve);
			}
			if (resolve) {
				this.resolveClass(clazz);
			}
			if (this.listener != null) {
				this.listener.onLoadClass(clazz);
			}
			return clazz;
		}
		return super.loadClass(name, resolve);
	}

	@Override
	public Class<?> findClass(final String name)
			throws ClassNotFoundException {
		final String file = name.replace('.', File.separatorChar)
				+ ".class";
		final int i = name.lastIndexOf('.');
		if (i != -1) {
			final String packageName = name.substring(0, i);
			if (this.getPackage(packageName) == null) {
				try {
					this.definePackage(
							packageName, null, null, null, null, null, null, null);
				} catch (final IllegalArgumentException e) {
				}
			}
		}
		byte[] b = null;
		try {
			b = this.loadClassData(file);
			if (b == null) {
				return null;
			}
			final Class<?> c = this.defineClass(name, b, 0, b.length);
			return c;
		} catch (final IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private byte[] loadClassData(final String name) throws IOException {
		final InputStream stream = ClassLoader.getSystemResourceAsStream(name);
		if (stream == null) {
			System.out.println(name + " was not found on the file system or classpath. This class and classes referenced by it cannot be instrumented.");
			return null;
		}
		final int size = stream.available();
		final byte buff[] = new byte[size];
		final DataInputStream in = new DataInputStream(stream);
		in.readFully(buff);
		in.close();
		return buff;
	}

	public void setListener(final ClassLoadingListener probeController) {
		this.listener = probeController;
	}

	public boolean isInstrumentedClass(final String name) {

		if (name.startsWith("kieker.examples.")) {
			return true;
		}

		if (name.startsWith("java.")
				|| name.startsWith("javax.")
				|| name.startsWith("sun.")
				|| name.startsWith("com.sun.")
				|| name.startsWith("org.w3c.")
				|| name.startsWith("org.xml.")
				|| name.startsWith("kieker.")
				|| name.startsWith("org.aim.")) {
			return false;
		}
		return true;
	}
}
