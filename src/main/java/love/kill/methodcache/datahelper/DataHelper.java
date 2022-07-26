package love.kill.methodcache.datahelper;

import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

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
	Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark, boolean nullable) throws Exception;


	/**
	 * 请求模型
	 * */
	interface ActualDataFunctional{
		/**
		 * 发起一次真实请求，缓存并返回该数据
		 * @return 请求数据
		 * */
		Object getActualData() throws Throwable;

		/**
		 * 数据过期时间，时间戳
		 * @return 过期时间
		 * */
		long getExpirationTime();
	}

	/**
	 * 格式化时间
	 * @param timeStamp 时间戳
	 * @return 格式化后的时间
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
	 * @param match 匹配规则
	 * @param select 筛选
	 * @return key
	 * */
	Map<String, Map<String,Object>> getCaches(String match,String select);


	/**
	 * 清空数据
	 *
	 * @param id 缓存ID
	 * @param cacheHashCode 缓存哈希值
	 * @return 删除的缓存
	 * */
	Map<String, Map<String,Object>> wipeCache(String id, String cacheHashCode);


	/**
	 * 筛选符合入参的缓存
	 * @param cacheMap 缓存数据
	 * @param cacheDataModel 待筛选的节点
	 * @param select 筛选
	 * @return 符合的缓存
	 * */
	@SuppressWarnings("unchecked")
	default Map<String, Map<String, Object>> filterDataModel(Map<String, Map<String, Object>> cacheMap, CacheDataModel cacheDataModel, String select) {
		if(!StringUtils.isEmpty(select)){
			String args = cacheDataModel.getArgs();
			if(!StringUtils.isEmpty(args) && !args.contains(select)){
				return cacheMap;
			}
		}

		Map<String,Object> keyMap = cacheMap.computeIfAbsent(cacheDataModel.getMethodSignature(), k -> {
			Map<String,Object> map = new HashMap<>();
			map.put("id",cacheDataModel.getId());
			map.put("remark",cacheDataModel.getRemark());
			return map;
		});

		List<Map<String, Object>> cacheInfoList = (List<Map<String, Object>>) keyMap.computeIfAbsent("cache", k -> new ArrayList<>());

		Map<String,Object> cacheInfo = new HashMap<>();
		cacheInfo.put("hashCode", cacheDataModel.getCacheHashCode());
		cacheInfo.put("args",cacheDataModel.getArgs());
		cacheInfo.put("data",cacheDataModel.getData());
		cacheInfo.put("cacheTime",cacheDataModel.getFormatCacheTime());
		cacheInfo.put("expireTime",cacheDataModel.getFormatExpireTime());
		cacheInfoList.add(cacheInfo);

		return cacheMap;
	}

}
