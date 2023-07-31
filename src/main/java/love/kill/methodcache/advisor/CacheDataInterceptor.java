package love.kill.methodcache.advisor;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.CacheData;
import love.kill.methodcache.annotation.CapitalExpiration;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.AnnotationUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * CacheData 拦截通知
 * 根据方法入参进行匹配，如果匹配命中且缓存数据未过期，则返回缓存数据
 *
 * @author Lycop
 */
public class CacheDataInterceptor implements MethodInterceptor {

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


	public CacheDataInterceptor(MethodcacheProperties methodcacheProperties, DataHelper dataHelper) {
		this.methodcacheProperties = methodcacheProperties;
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

		if (!methodcacheProperties.isEnable()) {
			return methodInvocation.proceed();
		}

		Method method = methodInvocation.getMethod();
		Object proxy = methodInvocation.getThis();
		Class<?> target = method.getDeclaringClass();

		if(getProxyClass(target) != proxy.getClass()){
			return methodInvocation.proceed();
		}

		CacheData cacheData = AnnotationUtil.getAnnotation(method, proxy.getClass(), CacheData.class);

		if (cacheData == null) {
			return methodInvocation.proceed();
		}

		boolean refresh = cacheData.refresh(); // 刷新数据
		long expiration = cacheData.expiration(); // 数据过期时间，毫秒
		long behindExpiration = cacheData.behindExpiration(); //  数据过期宽限期，毫秒
		CapitalExpiration capitalExpiration = cacheData.capitalExpiration(); // 数据过期时间累加基础
		boolean nullable = cacheData.nullable(); // 空返回
		String isolationSignal = dataHelper.threadLocal.get(); // 隔离标记
		return dataHelper.getData(proxy, methodInvocation.getMethod(),
				methodInvocation.getArguments(), isolationSignal, refresh, new DataHelper.ActualDataFunctional() {
					@Autowired
					public Object getActualData() throws Throwable {
						try {
							return methodInvocation.proceed();
						} catch (Throwable throwable) {
							throwable.printStackTrace();
							throw throwable;
						}
					}

					@Override
					public long getExpirationTime() {
						return expirationTime(expiration, behindExpiration, capitalExpiration);
					}
				}, cacheData.id(), cacheData.remark(), nullable);
	}


	/**
	 * 计算数据过期时间
	 */
	private static long expirationTime(long expiration, long behindExpiration, CapitalExpiration capitalExpiration) {

		if (expiration < 0L) {
			return -1L;
		}

		Calendar calendar = Calendar.getInstance();
		switch (capitalExpiration) {
			case YEAR:
				calendar.set(Calendar.MONTH, 0);
			case MONTH:
				calendar.set(Calendar.DATE, 1);
			case DAY:
				calendar.set(Calendar.HOUR_OF_DAY, 0);
			case HOUR:
				calendar.set(Calendar.MINUTE, 0);
			case MINUTE:
				calendar.set(Calendar.SECOND, 0);
		}

		int calendarAddType;
		switch (capitalExpiration) {
			case MINUTE:
				calendarAddType = Calendar.MINUTE;
				break;
			case HOUR:
				calendarAddType = Calendar.HOUR_OF_DAY;
				break;
			case DAY:
				calendarAddType = Calendar.DATE;
				break;
			case MONTH:
				calendarAddType = Calendar.MONTH;
				break;
			case YEAR:
				calendarAddType = Calendar.YEAR;
				break;
			default:
				calendarAddType = -1;
		}

		expiration += Math.random() * behindExpiration;


		if (calendarAddType != -1) {
			calendar.add(calendarAddType, 1);
		}

		return calendar.getTime().getTime() + expiration;
	}
}
