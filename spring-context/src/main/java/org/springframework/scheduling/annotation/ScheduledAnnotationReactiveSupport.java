/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.scheduling.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Helper class for @{@link ScheduledAnnotationBeanPostProcessor} to support reactive cases
 * without a dependency on optional classes.
 * @author Simon Baslé
 * @since 6.1.0 //FIXME
 */
abstract class ScheduledAnnotationReactiveSupport {

	static final boolean publisherPresent = ClassUtils.isPresent(
			"org.reactivestreams.Publisher", ScheduledAnnotationReactiveSupport.class.getClassLoader());

	static final boolean reactorPresent = ClassUtils.isPresent(
			"reactor.core.publisher.Flux", ScheduledAnnotationReactiveSupport.class.getClassLoader());

	static final boolean coroutinesReactorPresent = ClassUtils.isPresent(
			"kotlinx.coroutines.reactor.MonoKt", ScheduledAnnotationReactiveSupport.class.getClassLoader());

	/**
	 * Checks that if the method is reactive, it can be scheduled. Methods are considered
	 * eligible for reactive scheduling if they either return an instance of
	 * {@code Publisher} or are a Kotlin Suspending Function. If the method isn't matching
	 * these criteria then this check returns {@code false}.
	 * <p>For reactive scheduling, Reactor MUST be present at runtime. In the case of
	 * Kotlin, the Coroutine {@code kotlinx.coroutines.reactor} bridge MUST also be
	 * present at runtime (in order to invoke suspending functions as a {@code Mono}).
	 * Provided that is the case, this method returns {@code true}. Otherwise, it throws
	 * an {@code IllegalStateException}.
	 * @throws IllegalStateException if the method is reactive but Reactor and/or the
	 * Kotlin coroutines bridge are not present at runtime
	 */
	static boolean isReactive(Method method) {
		if (KotlinDetector.isKotlinPresent() && KotlinDetector.isSuspendingFunction(method)) {
			if (coroutinesReactorPresent) {
				return true;
			}
			else {
				throw new IllegalStateException("Kotlin suspending functions may only be annotated with @Scheduled"
						+ "if Reactor and the Reactor-Coroutine bridge (kotlinx.coroutines.reactor) are present at runtime");
			}
		}
		if (!publisherPresent) {
			return false;
		}
		if (!Publisher.class.isAssignableFrom(method.getReturnType())) {
			return false;
		}

		if (reactorPresent) {
			return true;
		}
		throw new IllegalStateException("Reactive methods returning a Publisher may only be annotated with @Scheduled"
				+ "if Reactor is present at runtime");
	}

	/**
	 * Turn the invocation of the provided {@code Method} into a {@code Publisher},
	 * either by reflectively invoking it and returning the resulting {@code Publisher}
	 * or by converting a Kotlin suspending function into a Publisher via
	 * {@link CoroutinesUtils}.
	 * The {@link #isReactive(Method)} check is a precondition to calling this method.
	 */
	static Publisher<?> getPublisherFor(Method method, Object bean) {
		if (KotlinDetector.isKotlinPresent() && KotlinDetector.isSuspendingFunction(method)) {
			//Note that suspending functions declared without args have a single Continuation parameter in reflective inspection
			Assert.isTrue(method.getParameterCount() == 1,"Kotlin suspending functions may only be annotated "
					+ "with @Scheduled if declared without arguments");

			return CoroutinesUtils.invokeSuspendingFunction(method, bean, (Object[]) method.getParameters());
		}

		Assert.isTrue(method.getParameterCount() == 0, "Reactive methods may only be annotated with "
				+ "@Scheduled if declared without arguments");
		Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
		try {
			ReflectionUtils.makeAccessible(invocableMethod);
			Object r = invocableMethod.invoke(bean);
			return (Publisher<?>) r;
		}
		catch (InvocationTargetException ex) {
			throw new IllegalArgumentException("Cannot obtain a Publisher from the @Scheduled reactive method", ex.getTargetException());
		}
		catch (IllegalAccessException ex) {
			throw new IllegalArgumentException("Cannot obtain a Publisher from the @Scheduled reactive method", ex);
		}
	}

	/**
	 * Encapsulates the logic of {@code @Scheduled} on reactive types, using Reactor.
	 * The {@link ScheduledAnnotationReactiveSupport#isReactive(Method)} check is a
	 * precondition to instantiating this class.
	 */
	static class ReactiveTask {

		private final Publisher<?> publisher;
		private final Duration initialDelay;
		private final Duration otherDelay;
		private final boolean isFixedRate;
		protected final String checkpoint;
		private final Disposable.Swap disposable;

		private final Log logger = LogFactory.getLog(getClass());


		protected ReactiveTask(Method method, Object bean, Duration initialDelay, Duration otherDelay, boolean isFixedRate) {
			this.publisher = getPublisherFor(method, bean);

			this.initialDelay = initialDelay;
			this.otherDelay = otherDelay;
			this.isFixedRate = isFixedRate;

			this.disposable = Disposables.swap();
			this.checkpoint = "@Scheduled '"+ method.getName() + "()' in bean '"
					+ method.getDeclaringClass().getName() + "'";
		}

		private Mono<Void> safeExecutionMono() {
			Mono<Void> executionMono;
			if (this.publisher instanceof Mono) {
				executionMono = Mono.from(this.publisher).then();
			}
			else {
				executionMono = Flux.from(this.publisher).then();
			}
			if (logger.isWarnEnabled()) {
				executionMono = executionMono.doOnError(ex -> logger.warn(
						"Ignored error in publisher from " + this.checkpoint, ex));
			}
			executionMono = executionMono.onErrorComplete();
			return executionMono;
		}

		public void subscribe() {
			if (this.disposable.isDisposed()) {
				return;
			}

			final Mono<Void> executionMono = safeExecutionMono();
			Flux<Void> scheduledFlux;
			if (this.isFixedRate) {
				scheduledFlux = Flux.interval(this.initialDelay, this.otherDelay)
						.flatMap(it -> executionMono);
			}
			else {
				scheduledFlux = Mono.delay(this.otherDelay).then(executionMono).repeat();
				if (!this.initialDelay.isZero()) {
					scheduledFlux = Flux.concat(
							Mono.delay(this.initialDelay).then(executionMono),
							scheduledFlux
					);
				}
				else {
					scheduledFlux = Flux.concat(executionMono, scheduledFlux);
				}
			}
			// Subscribe and ensure that errors can be traced back to the @Scheduled via a checkpoint
			if (this.disposable.isDisposed()) {
				return;
			}
			this.disposable.update(scheduledFlux.checkpoint(this.checkpoint)
					.subscribe(it -> {}, ex -> ReactiveTask.this.logger.error("Unexpected error occurred in scheduled reactive task", ex)));
		}

		public void cancel() {
			this.disposable.dispose();
		}

		@Override
		public String toString() {
			return this.checkpoint;
		}
	}
}
