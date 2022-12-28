package com.udacity.webcrawler.profiler;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

	private final Clock clock;
	private final ProfilingState state = new ProfilingState();
	private final ZonedDateTime startTime;

	@Inject
	ProfilerImpl(Clock clock) {
		this.clock = Objects.requireNonNull(clock);
		this.startTime = ZonedDateTime.now(clock);
	}

	private boolean checkProfiledClass(Class<?> klass) {
		List<Method> methods = new ArrayList<>(Arrays.asList(klass.getDeclaredMethods()));
		if (methods.isEmpty()) {
			return false;
		}
		return methods.stream().anyMatch(x -> x.getAnnotation(Profiled.class) != null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T wrap(Class<T> klass, T delegate) {
		Objects.requireNonNull(klass);
		// Check method has annotation is Profiled
		if (!checkProfiledClass(klass)) {
			throw new IllegalArgumentException(klass.getName() + "this method doesn't have profiled methods");
		}
		// TODO: Use a dynamic proxy (java.lang.reflect.Proxy) to "wrap" the delegate in
		// a
		// ProfilingMethodInterceptor and return a dynamic proxy from this method.
		// See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.

		InvocationHandler handler = new ProfilingMethodInterceptor(clock, delegate, state);

		Object proxy = Proxy.newProxyInstance(ProfilerImpl.class.getClassLoader(), new Class[] { klass },
				handler);
		return (T) proxy;
	}

	@Override
	public void writeData(Path path) throws IOException {
		Objects.requireNonNull(path);
		// TODO: Write the ProfilingState data to the given file path. If a file already
		// exists at that
		// path, the new data should be appended to the existing file.
		try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			writeData(writer);
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void writeData(Writer writer) throws IOException {
		writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
		writer.write(System.lineSeparator());
		state.write(writer);
		writer.write(System.lineSeparator());
	}
}
