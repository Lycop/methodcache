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
package love.kill.methodcache.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 分页工具类
 *
 * @author Lycop
 */
public class PaginationUtil {
	/**
	 * 手动分页
	 *
	 * @param dataModelSet 待分页的 Set 集合
	 * @param pageSize 每页大小
	 * @param pageNo 当前页码
	 * @param <T> 集合元素的类型
	 * @return 分页后的 Set 集合
	 */
	public static <T> Set<T> paginate(Set<T> dataModelSet, int pageSize, int pageNo) {

		if(dataModelSet == null || dataModelSet.isEmpty()){
			return new HashSet<>();
		}

		List<T> dataList = new ArrayList<>(dataModelSet);
		int startIndex = (pageNo - 1) * pageSize; // 当前页的起始索引
		int endIndex = Math.min(startIndex + pageSize, dataList.size()); // 计当前页的结束索引
		if (startIndex >= dataList.size()) {
			// 超出集合范围，返回空集合
			return new HashSet<>();
		}

		return new HashSet<>(dataList.subList(startIndex, endIndex));
	}
}
