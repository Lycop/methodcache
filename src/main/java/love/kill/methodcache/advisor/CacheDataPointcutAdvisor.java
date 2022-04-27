package love.kill.methodcache.advisor;

import love.kill.methodcache.annotation.CacheData;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;

import java.lang.reflect.Method;

/**
 * CacheData 注解切点断言
 *
 * @author Lycop
 */
public class CacheDataPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor {

	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		// 拦截被 @CacheData 注解的接口或类
		return method.isAnnotationPresent(CacheData.class) || targetClass.isAnnotationPresent(CacheData.class);
	}
}
