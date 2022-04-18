package love.kill.methodcache.aspect;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.CacheData;
import love.kill.methodcache.annotation.CapitalExpiration;
import love.kill.methodcache.util.DataHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.*;


/**
 * 数据缓存拦截器
 **/
@Aspect
public class CacheMethodAspect {

	private static Logger logger = LoggerFactory.getLogger(CacheMethodAspect.class);



	private MethodcacheProperties methodcacheProperties;

	private DataHelper dataHelper;

	public CacheMethodAspect(MethodcacheProperties methodcacheProperties) {
		this.methodcacheProperties = methodcacheProperties;
	}

	public DataHelper getDataHelper() {
		return dataHelper;
	}

	public void setDataHelper(DataHelper dataHelper) {
		this.dataHelper = dataHelper;
	}

	/**
	 * 方法缓存
	 */
	@Around("@annotation(love.kill.methodcache.annotation.CacheData)")
	public Object methodcache(ProceedingJoinPoint joinPoint) throws Throwable {
		Object[] args = joinPoint.getArgs(); //方法入参实体
		Signature signature = joinPoint.getSignature();
		if(!methodcacheProperties.isEnable() || !(signature instanceof MethodSignature)){
			return joinPoint.proceed(args);
		}

		MethodSignature methodSignature = (MethodSignature) signature;
		Method method = joinPoint.getTarget().getClass().getMethod(methodSignature.getName(), methodSignature.getParameterTypes());
		CacheData cacheData = method.getAnnotation(CacheData.class);

		try {
			boolean refresh = cacheData.refresh(); // 刷新数据
			long expiration = cacheData.expiration(); // 数据过期时间，毫秒
			long behindExpiration = cacheData.behindExpiration(); //  数据过期宽限期，毫秒
			CapitalExpiration capitalExpiration = cacheData.capitalExpiration(); // 数据过期时间累加基础

			return dataHelper.getData(method,args,refresh,new DataHelper.ActualDataFunctional(){
				@Autowired
				public Object getActualData() {
					try {
						return joinPoint.proceed(args);
					} catch (Throwable throwable) {
						// TODO: 2022/3/23  
						throwable.printStackTrace();
					}
					return null;
				}

				@Override
				public long getExpirationTime() {
					return expirationTime(expiration,behindExpiration,capitalExpiration);
				}
			});


		}catch (Exception e){
			logger.error("数据缓存出现异常：" + e.getMessage());
			e.printStackTrace();
			joinPoint.proceed(args);
		}

		return joinPoint.proceed(args);
	}



	private static long expirationTime(long expiration, long behindExpiration, CapitalExpiration capitalExpiration) {

		if(expiration < 0L){
			return -1L;
		}

		Calendar calendar = Calendar.getInstance();
		switch (capitalExpiration){
			case YEAR:
				calendar.set(Calendar.MONTH, 0);;
			case MONTH:
				calendar.set(Calendar.DATE,0);
			case DAY:
				calendar.set(Calendar.HOUR_OF_DAY,0);
			case HOUR:
				calendar.set(Calendar.MINUTE,0);
			case MINUTE:
				calendar.set(Calendar.SECOND,0);
		}

		int calendarAddType;
		switch (capitalExpiration){
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


		if(calendarAddType != -1){
			calendar.add(calendarAddType,1);
		}

		return calendar.getTime().getTime() + expiration;
	}
}
