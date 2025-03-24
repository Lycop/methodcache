/*
 * Copyright 2025 Lycop
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
package love.kill.methodcache.annotation;


import java.lang.annotation.*;

/**
 * 缓存隔离
 *
 * 方法内部缓存的数据，仅会在该方法及子方法内部可见
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CacheIsolation {

	/**
	 * 隔离策略
	 * N：不隔离，T：线程隔离，默认 N
	 *
	 * @return 隔离策略
	 */
	char isolationStrategy() default 'N';

}
