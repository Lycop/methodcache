package love.kill.methodcache.advisor;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.CacheData;
import love.kill.methodcache.annotation.CapitalExpiration;
import love.kill.methodcache.datahelper.DataHelper;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.Calendar;

/**
 * CacheData 拦截通知
 * 根据方法入参进行匹配，如果匹配命中且缓存数据未过期，则返回缓存数据
 *
 * @author Lycop
 */
public class CacheDataInterceptor implements MethodInterceptor {

	private MethodcacheProperties methodcacheProperties;

	private DataHelper dataHelper;

	public CacheDataInterceptor(MethodcacheProperties methodcacheProperties, DataHelper dataHelper) {
		this.methodcacheProperties = methodcacheProperties;
		this.dataHelper = dataHelper;
	}

	@Override
	public Object invoke(MethodInvocation methodInvocation) throws Throwable {

		if (!methodcacheProperties.isEnable()) {
			return methodInvocation.proceed();
		}

		Method method = methodInvocation.getMethod();
		Object[] args = methodInvocation.getArguments(); //方法入参实体
		CacheData cacheData = method.getAnnotation(CacheData.class);

		if (cacheData == null) {
			return methodInvocation.proceed();
		}

		boolean refresh = cacheData.refresh(); // 刷新数据
		long expiration = cacheData.expiration(); // 数据过期时间，毫秒
		long behindExpiration = cacheData.behindExpiration(); //  数据过期宽限期，毫秒
		CapitalExpiration capitalExpiration = cacheData.capitalExpiration(); // 数据过期时间累加基础
		boolean nullable = cacheData.nullable(); // 空返回

		return dataHelper.getData(method, args, refresh, new DataHelper.ActualDataFunctional() {
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
