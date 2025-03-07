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
