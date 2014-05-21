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

package org.springframework.cache.jcache.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.interceptor.AbstractCacheInvoker;
import org.springframework.cache.interceptor.BasicCacheOperation;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.cache.jcache.model.CachePutOperation;
import org.springframework.cache.jcache.model.CacheRemoveAllOperation;
import org.springframework.cache.jcache.model.CacheRemoveOperation;
import org.springframework.cache.jcache.model.CacheResultOperation;
import org.springframework.cache.jcache.model.JCacheOperation;
import org.springframework.util.Assert;

/**
 * Base class for JSR-107 caching aspects, such as the {@link JCacheInterceptor}
 * or an AspectJ aspect.
 *
 * <p>Use the Spring caching abstraction for cache-related operations. No JSR-107
 * {@link javax.cache.Cache} or {@link javax.cache.CacheManager} are required to
 * process standard JSR-107 cache annotations.
 *
 * <p>The {@link JCacheOperationSource} is used for determining caching operations
 *
 * <p>A cache aspect is serializable if its {@code JCacheOperationSource} is serializable.
 *
 * @author Stephane Nicoll
 * @since 4.1
 * @see org.springframework.cache.interceptor.CacheAspectSupport
 * @see KeyGeneratorAdapter
 * @see CacheResolverAdapter
 */
public class JCacheAspectSupport extends AbstractCacheInvoker implements InitializingBean {

	protected final Log logger = LogFactory.getLog(getClass());

	private JCacheOperationSource cacheOperationSource;

	private boolean initialized = false;

	private CacheResultInterceptor cacheResultInterceptor;

	private CachePutInterceptor cachePutInterceptor;

	private CacheRemoveEntryInterceptor cacheRemoveEntryInterceptor;

	private CacheRemoveAllInterceptor cacheRemoveAllInterceptor;

	public void setCacheOperationSource(JCacheOperationSource cacheOperationSource) {
		Assert.notNull(cacheOperationSource);
		this.cacheOperationSource = cacheOperationSource;
	}

	/**
	 * Return the CacheOperationSource for this cache aspect.
	 */
	public JCacheOperationSource getCacheOperationSource() {
		return this.cacheOperationSource;
	}

	public void afterPropertiesSet() {
		Assert.state(this.cacheOperationSource != null, "The 'cacheOperationSource' property is required: " +
				"If there are no cacheable methods, then don't use a cache aspect.");
		Assert.state(this.getErrorHandler() != null, "The 'errorHandler' is required.");

		this.cacheResultInterceptor = new CacheResultInterceptor(getErrorHandler());
		this.cachePutInterceptor = new CachePutInterceptor(getErrorHandler());
		this.cacheRemoveEntryInterceptor = new CacheRemoveEntryInterceptor(getErrorHandler());
		this.cacheRemoveAllInterceptor  = new CacheRemoveAllInterceptor(getErrorHandler());

		this.initialized = true;
	}


	protected Object execute(CacheOperationInvoker invoker, Object target, Method method, Object[] args) {
		// check whether aspect is enabled
		// to cope with cases where the AJ is pulled in automatically
		if (this.initialized) {
			Class<?> targetClass = getTargetClass(target);
			JCacheOperation<?> operation = getCacheOperationSource().getCacheOperation(method, targetClass);
			if (operation != null) {
				CacheOperationInvocationContext<?> context =
						createCacheOperationInvocationContext(target, args, operation);
				return execute(context, invoker);
			}
		}

		return invoker.invoke();
	}

	@SuppressWarnings("unchecked")
	private CacheOperationInvocationContext<?> createCacheOperationInvocationContext(Object target,
			Object[] args,
			JCacheOperation<?> operation) {
		return new DefaultCacheInvocationContext<Annotation>(
				(JCacheOperation<Annotation>) operation, target, args);
	}

	private Class<?> getTargetClass(Object target) {
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
		if (targetClass == null && target != null) {
			targetClass = target.getClass();
		}
		return targetClass;
	}

	@SuppressWarnings("unchecked")
	private Object execute(CacheOperationInvocationContext<?> context,
			CacheOperationInvoker invoker) {
		BasicCacheOperation operation = context.getOperation();
		if (operation instanceof CacheResultOperation) {
			return cacheResultInterceptor.invoke(
					(CacheOperationInvocationContext<CacheResultOperation>) context, invoker);
		}
		else if (operation instanceof CachePutOperation) {
			return cachePutInterceptor.invoke(
					(CacheOperationInvocationContext<CachePutOperation>) context, invoker);
		}
		else if (operation instanceof CacheRemoveOperation) {
			return cacheRemoveEntryInterceptor.invoke(
					(CacheOperationInvocationContext<CacheRemoveOperation>) context, invoker);
		}
		else if (operation instanceof CacheRemoveAllOperation) {
			return cacheRemoveAllInterceptor.invoke(
					(CacheOperationInvocationContext<CacheRemoveAllOperation>) context, invoker);
		}
		else {
			throw new IllegalArgumentException("Could not handle " + operation);
		}
	}

}