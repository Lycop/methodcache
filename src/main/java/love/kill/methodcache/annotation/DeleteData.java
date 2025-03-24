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
 * 删除缓存数据
 *
 * 被注解的方法调用成功后，清除ID对应的缓存
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Inherited
public @interface DeleteData {

	/**
	 * 缓存ID
	 *
	 * @return 缓存ID
	 */
	String[] id();
}
