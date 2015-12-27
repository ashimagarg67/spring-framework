/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cache.interceptor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.util.Assert;

/**
 * Base class for cache operations.
 *
 * @author Costin Leau
 * @author Stephane Nicoll
 * @since 3.1
 */
public abstract class CacheOperation implements BasicOperation {

    private final String name;

    private final Set<String> cacheNames;

    private final String key;

    private final String keyGenerator;

    private final String cacheManager;

    private final String cacheResolver;

    private final String condition;

    private String toString;

    protected CacheOperation(Builder b) {
        this.name = b.name;
        this.cacheNames = b.cacheNames;
        this.key = b.key;
        this.keyGenerator = b.keyGenerator;
        this.cacheManager = b.cacheManager;
        this.cacheResolver = b.cacheResolver;
        this.condition = b.condition;
    }

    public String getName() {
        return this.name;
    }


    @Override
    public Set<String> getCacheNames() {
        return this.cacheNames;
    }


    public String getKey() {
        return this.key;
    }


    public String getKeyGenerator() {
        return this.keyGenerator;
    }


    public String getCacheManager() {
        return this.cacheManager;
    }


    public String getCacheResolver() {
        return this.cacheResolver;
    }


    public String getCondition() {
        return this.condition;
    }


    /**
     * This implementation compares the {@code toString()} results.
     *
     * @see #toString()
     */
    @Override
    public boolean equals(Object other) {
        return (other instanceof CacheOperation && toString().equals(other.toString()));
    }

    /**
     * This implementation returns {@code toString()}'s hash code.
     *
     * @see #toString()
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Return an identifying description for this cache operation.
     * <p>Has to be overridden in subclasses for correct {@code equals}
     * and {@code hashCode} behavior. Alternatively, {@link #equals}
     * and {@link #hashCode} can be overridden themselves.
     */
    @Override
    public String toString() {
        String result = toString;
        if (result == null) {
            result = getOperationDescription().toString();
            toString = result;
        }
        return result;
    }

    /**
     * Return an identifying description for this caching operation.
     * <p>Available to subclasses, for inclusion in their {@code toString()} result.
     */
    protected StringBuilder getOperationDescription() {
        StringBuilder result = new StringBuilder(getClass().getSimpleName());
        result.append("[").append(this.name);
        result.append("] caches=").append(this.cacheNames);
        result.append(" | key='").append(this.key);
        result.append("' | keyGenerator='").append(this.keyGenerator);
        result.append("' | cacheManager='").append(this.cacheManager);
        result.append("' | cacheResolver='").append(this.cacheResolver);
        result.append("' | condition='").append(this.condition).append("'");
        return result;
    }

    public abstract static class Builder {

        private String name = "";

        private Set<String> cacheNames = Collections.emptySet();

        private String key = "";

        private String keyGenerator = "";

        private String cacheManager = "";

        private String cacheResolver = "";

        private String condition = "";

        public void setName(String name) {
            Assert.hasText(name);
            this.name = name;
        }

        public void setCacheName(String cacheName) {
            Assert.hasText(cacheName);
            this.cacheNames = Collections.singleton(cacheName);
        }

        public void setCacheNames(String... cacheNames) {
            this.cacheNames = new LinkedHashSet<String>(cacheNames.length);
            for (String cacheName : cacheNames) {
                Assert.hasText(cacheName, "Cache name must be non-null if specified");
                this.cacheNames.add(cacheName);
            }
        }

        public Set<String> getCacheNames() {
            return cacheNames;
        }

        public void setKey(String key) {
            Assert.notNull(key);
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public String getKeyGenerator() {
            return keyGenerator;
        }

        public String getCacheManager() {
            return cacheManager;
        }

        public String getCacheResolver() {
            return cacheResolver;
        }

        public void setKeyGenerator(String keyGenerator) {
            Assert.notNull(keyGenerator);
            this.keyGenerator = keyGenerator;
        }

        public void setCacheManager(String cacheManager) {
            Assert.notNull(cacheManager);
            this.cacheManager = cacheManager;
        }

        public void setCacheResolver(String cacheResolver) {
            Assert.notNull(cacheManager);
            this.cacheResolver = cacheResolver;
        }

        public void setCondition(String condition) {
            Assert.notNull(condition);
            this.condition = condition;
        }

        public abstract CacheOperation build();
    }

}
