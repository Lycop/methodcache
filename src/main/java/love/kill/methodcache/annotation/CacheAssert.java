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
public interface CacheAssert<T> {

	/**
	 * 断言是否取缓存的数据
	 *
	 * @param args 			方法入参
	 * @param cacheData 	已缓存数据
	 * @return 				断言结果，true表示使用该缓存，false表示不使用缓存，而是重新发起一次实际的请求
	 */
	default boolean assertCacheData(Object[] args, T cacheData){
		return true;
	}

	/**
	 * 断言是否缓存结果数据
	 *
	 * @param resultData 	执行结果数据
	 * @return 				断言结果，true表示缓存，false表示不缓存
	 */
	default boolean assertCacheResultData(T resultData){
		return true;
	}


	/**
	 * 判断数据类型是否符合泛型 T
	 *
	 * @param  o 	待判断的对象
	 * @return 		符合泛型
	 */
	default boolean isTClass(Object o) {
		Type[] genericInterfaces = getClass().getGenericInterfaces();
		for (Type type : genericInterfaces) {
			if (type instanceof ParameterizedType) {
				ParameterizedType parameterizedType = (ParameterizedType) type;
				if (CacheAssert.class.equals(parameterizedType.getRawType())) {
					Class tClass = (Class) (parameterizedType.getActualTypeArguments()[0]);
					return (tClass.isAssignableFrom(o.getClass()));
				}
			}
		}
		return false;
	}
}
