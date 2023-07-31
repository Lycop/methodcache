package love.kill.methodcache.advisor;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.DeleteData;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.AnnotationUtil;
import love.kill.methodcache.util.ThreadPoolBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * DeleteData 拦截通知
 * 方法执行完毕后，删除指定的缓存
 *
 * @author Lycop
 */
public class DeleteDataInterceptor implements MethodInterceptor {

	private MethodcacheProperties methodcacheProperties;

	/**
	 * 数据Helper
	 */
	private DataHelper dataHelper;

	/**
	 * 代理类
	 *
	 * 值：<被代理类(或接口), 代理类>
	 */
	private static Map<Class<?> ,Class<?>> targetProxyClass = new HashMap<>();

	/**
	 * 清除缓存线程池
	 */
	private ExecutorService deleteCacheExecutorService = ThreadPoolBuilder.buildDefaultThreadPool();

	public DeleteDataInterceptor(MethodcacheProperties methodcacheProperties, DataHelper dataHelper) {
		this.methodcacheProperties = methodcacheProperties;
		this.dataHelper = dataHelper;
	}

	//
	synchronized private static Class getProxyClass(Class<?> target){

		for(Class<?> key : targetProxyClass.keySet()){
			if(key.isAssignableFrom(target) || target.isAssignableFrom(key)){
				return targetProxyClass.get(key);
			}
		}
		return null;
	}

	synchronized public static boolean setProxyClass(Class<?> target, Class<?> proxy) {

		Class proxyClass = getProxyClass(target);

		if(proxyClass != null){
			return proxyClass == proxy;
		}

		targetProxyClass.put(target, proxy);
		return true;
	}

	@Override
	public Object invoke(MethodInvocation methodInvocation) throws Throwable {

		if (!methodcacheProperties.isEnable()) {
			return methodInvocation.proceed();
		}

		Method method = methodInvocation.getMethod();
		Object proxy = methodInvocation.getThis();
		Class<?> target = method.getDeclaringClass();

		if(getProxyClass(target) != proxy.getClass()){
			return methodInvocation.proceed();
		}

		DeleteData deleteData = AnnotationUtil.getAnnotation(method, proxy.getClass(), DeleteData.class);

		if (deleteData == null) {
			return methodInvocation.proceed();
		}

		String[] ids = deleteData.id();
		if (ids.length <= 0) {
			return methodInvocation.proceed();
		}

		Object result;
		try {
			result = methodInvocation.proceed();
			for (String id : ids) {
				if (StringUtils.isEmpty(id)) {
					continue;
				}
				deleteCacheExecutorService.execute(() -> {
					dataHelper.wipeCache(id, null);
				});
			}
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			throw throwable;
		}
		return result;
	}
}
