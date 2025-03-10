package love.kill.methodcache.annotation;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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
	 */
	default boolean doAssert(T data){
		return true;
	}
}
