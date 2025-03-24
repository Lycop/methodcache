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
package love.kill.methodcache.constant;


/**
 * URI 常量
 *
 * @author Lycop
 */
public class ControllerURI {

	/**
	 * 方法缓存
	 */
	public static final String METHOD_CACHE = "/method/cache";

	/************************************************* 缓存统计 *************************************************/

	/**
	 * 缓存统计
	 */
	public static final String CACHE_STATISTICS = ControllerURI.METHOD_CACHE + "/statistics";

	/************************************************* 版本信息 *************************************************/

	/**
	 * 版本信息
	 */
	public static final String CACHE_VERSION = ControllerURI.METHOD_CACHE + "/version";

}

