package love.kill.methodcache.advisor;

import love.kill.methodcache.annotation.CacheIsolation;
import love.kill.methodcache.constant.IsolationStrategy;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.AnnotationUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/**
 * CacheIsolation 拦截通知
 *
 * @author Lycop
 */
public class CacheIsolationInterceptor implements MethodInterceptor {


	private DataHelper dataHelper;

	/**
	 * 代理类
	 *
	 * 值：<被代理类(或接口), 代理类>
	 */
	private static Map<Class<?> ,Class<?>> targetProxyClass = new HashMap<>();


	public CacheIsolationInterceptor( DataHelper dataHelper) {
		this.dataHelper = dataHelper;
	}


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

		Method method = methodInvocation.getMethod();
		Object proxy = methodInvocation.getThis();
		Class<?> target = method.getDeclaringClass();

		if(getProxyClass(target) != proxy.getClass()){
			return methodInvocation.proceed();
		}

		CacheIsolation cacheIsolation = AnnotationUtil.getAnnotation(method, proxy.getClass(), CacheIsolation.class);

		if (cacheIsolation == null) {
			return methodInvocation.proceed();
		}


		if (IsolationStrategy.THREAD == cacheIsolation.isolationStrategy()) {
			String isolationSignal; // 隔离标记
			boolean setIsolationSignal = false; // 当前方法设置了"隔离标记"
			try {
				isolationSignal = dataHelper.threadLocal.get();
				if(StringUtils.isEmpty(isolationSignal)){
					isolationSignal = UUID.randomUUID().toString() + "@" + String.valueOf(Thread.currentThread().getId());
					dataHelper.threadLocal.set(isolationSignal);
					setIsolationSignal = true;
				}

				return methodInvocation.proceed();

			}catch (Exception e){
				e.printStackTrace();
			}finally {
				if(setIsolationSignal){
					// 谁设置，谁清除
					dataHelper.threadLocal.remove();
				}
			}

		}

		return methodInvocation.proceed();
	}
}
