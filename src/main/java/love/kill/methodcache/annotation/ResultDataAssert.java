package love.kill.methodcache.annotation;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 返回结果断言
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public interface ResultDataAssert<T> {

	/**
	 * 断言
	 *
	 * @param resultData 	执行结果
	 * @return 				断言结果
	 */
	boolean assertResultData(T resultData);


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
				if (ResultDataAssert.class.equals(parameterizedType.getRawType())) {
					Class tClass = (Class) (parameterizedType.getActualTypeArguments()[0]);
					return (tClass.isAssignableFrom(o.getClass()));
				}
			}
		}
		return false;
	}
}
