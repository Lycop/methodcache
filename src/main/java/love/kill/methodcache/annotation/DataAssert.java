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

/**
 * (实际的)请求返回值断言
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public interface DataAssert<T> {

	/**
	 * 断言
	 *
	 * @param data 待断言的数据
	 * @return 	断言结果
	 */
	default boolean doAssert(T data){
		return true;
	}
}
