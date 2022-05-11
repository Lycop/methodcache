package love.kill.methodcache.datahelper;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 数据缓存
 *
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public interface DataHelper {

	SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


	/**
	 * 获取数据
	 * @param method 方法
	 * @param args 请求参数
	 * @param refreshData 刷新数据
	 * @param actualDataFunctional 请求模型
	 * @return 数据
	 * */
	Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark) throws Exception;


	/**
	 * 请求模型
	 * */
	interface ActualDataFunctional{
		/**
		 * 发起一次真实请求，缓存并返回该数据
		 * */
		Object getActualData() throws Throwable;

		/**
		 * 数据过期时间，时间戳
		 * */
		long getExpirationTime();
	}

	/**
	 * 格式化时间
	 * */
	default String formatDate(long timeStamp){
		try {
			return formatDate.format(new Date(timeStamp));
		}catch (Exception e){
			e.printStackTrace();
			return String.valueOf(timeStamp);
		}
	}

	/**
	 * 获取缓存
	 *
	 * @return key
	 * */
	Map<String, Map<String,Object>> getCaches(String key);


	/**
	 * 清空数据
	 *
	 * @param cacheHashCode 缓存哈希值
	 * */
	void wipeCache(int cacheHashCode);
}
