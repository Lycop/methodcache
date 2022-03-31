package love.kill.methodcache.util;

import java.util.Objects;

/**
 * redis锁工具类
 *
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public interface DataHelper {

	/**
	 * 获取数据
	 * */
	Object getData(String key,boolean refreshData,ActualDataFunctional actualDataFunctional);

	interface ActualDataFunctional{
		/**
		 *
		 * */
		Object getActualData();

		/**
		 *
		 * */
		long getExpirationTime();
	}
}
