/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.web.service.invoker;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;

/**
 * {@link HttpServiceArgumentResolver} for suspend method.
 *
 * @author Wonwoo Lee
 * @since 6.0
 */
public class CoroutinesSuspendArgumentResolver implements HttpServiceArgumentResolver {

	@Override
	public boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {

		if ("kotlin.coroutines.Continuation".equals(parameter.getParameterType().getName()) && argument != null) {
			requestValues.setContinuation(argument);
			return true;
		}

		return false;
	}
}
