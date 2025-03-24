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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 缓存断言
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public interface CacheDataAssert<T> extends DataAssert<T> {
	/**
	 * 判断数据类型是否符合泛型
	 *
	 * @param o 待判断的对象
	 * @return 	符合泛型
	 */
	default boolean isClass(Object o) {
		Type[] genericInterfaces = getClass().getGenericInterfaces();
		for (Type type : genericInterfaces) {
			if (type instanceof ParameterizedType) {
				ParameterizedType parameterizedType = (ParameterizedType) type;
				if (CacheDataAssert.class.equals(parameterizedType.getRawType())) {
					Class tClass = (Class) (parameterizedType.getActualTypeArguments()[0]);
					return (tClass.isAssignableFrom(o.getClass()));
				}
			}
		}
		return false;
	}
}
