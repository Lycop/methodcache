/*
 * Copyright 2012-2017 the original author or authors.
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
 *
 * Derived from org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer (2012-2017, Apache 2.0).
 *
 * This modified version (c) Lycop 2025, also Apache 2.0.
 *
 * Mods:
 * 1. Package: org.springframework.boot.autoconfigure.data.redis -> love.kill.methodcache.redis
 * 2. Interface: LettuceClientConfigurationBuilderCustomizer -> MCLettuceClientConfigurationBuilderCustomizer
 */
package love.kill.methodcache.redis;

import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link LettuceClientConfiguration} via a {@link LettuceClientConfigurationBuilder
 * LettuceClientConfiguration.LettuceClientConfigurationBuilder} whilst retaining default
 * auto-configuration.
 *
 * @author Mark Paluch
 * @since 2.0.0
 */
@FunctionalInterface
public interface MCLettuceClientConfigurationBuilderCustomizer {

	/**
	 * Customize the {@link LettuceClientConfigurationBuilder}.
	 * @param clientConfigurationBuilder the builder to customize
	 */
	void customize(LettuceClientConfigurationBuilder clientConfigurationBuilder);

}
