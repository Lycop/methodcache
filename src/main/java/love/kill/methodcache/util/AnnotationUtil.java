package love.kill.methodcache.util;

import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 注解工具类
 *
 * @author Lycop
 */
public class AnnotationUtil {

	/**
	 * 获取方法上的指定注解
	 *
	 * 优先级：直接修饰 〉继承 〉代理对象
	 *
	 * @param method 			方法
	 * @param proxy           	方法所在的类
	 * @param annotationClass   注解类
	 * @param <T>				注解类型
	 * @return 注解
	 */
	public static <T extends Annotation> T getAnnotation(Method method, Class<?> proxy, Class<T> annotationClass) {

		T annotation = method.getDeclaredAnnotation(annotationClass); // 直接修饰

		if (annotation != null) {
			return annotation;
		}


		annotation = AnnotationUtils.findAnnotation(method, annotationClass); // 继承的注解

		if (annotation != null) {
			return annotation;
		}

		if (proxy == null) {
			return null;

		}

		for (Method proxyMethod : proxy.getMethods()) {

			T targetAnnotation = getAnnotation(proxyMethod, null, annotationClass);
			if (targetAnnotation == null) {
				continue;
			}

			if (theSameAs(method, proxyMethod)) {
				return targetAnnotation;
			}
		}

		return annotation;
	}


	/**
	 * 比较两个方法是否一致
	 *
	 * @param method 		方法
	 * @param another 		另一个方法
	 * @return 方法一致
	 */
	private static boolean theSameAs(Method method, Method another) {

		// 比较方法名
		if (!method.getName().equals(another.getName())) {
			return false;
		}

		// 比较方法参数
		if (method.getParameterCount() != another.getParameterCount()) {
			return false;
		}
		Class<?>[] subParams = method.getParameterTypes();
		Class<?>[] superParams = another.getParameterTypes();
		for (int i = 0; i < subParams.length; i++) {
			if (!subParams[i].equals(superParams[i])) {
				return false;
			}
		}

		// 比较返回值类型
		if (!method.getReturnType().equals(another.getReturnType())) {
			return method.getReturnType().isAssignableFrom(another.getReturnType());
		}

		return true;
	}
}
