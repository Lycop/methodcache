/*
 * Copyright 2025 Lycop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package love.kill.methodcache.advisor;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.CacheData;
import love.kill.methodcache.annotation.CapitalExpiration;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.AnnotationUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.*;

/**
 * CacheData 拦截通知
 * 根据方法入参进行匹配，如果匹配命中且缓存数据未过期，则返回缓存数据
 *
 * @author Lycop
 */
public class CacheDataInterceptor implements MethodInterceptor {


	private static Logger logger = LoggerFactory.getLogger(MethodInterceptor.class);

	/**
	 * 配置
	 * */
	private MethodcacheProperties methodcacheProperties;

	/**
	 * 数据Helper
	 */
	private DataHelper dataHelper;

	/**
	 * 代理类
	 *
	 * 值：＜被代理类(或接口), 代理类＞
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
		boolean shared = cacheData.shared(); // 共享式缓存
		Class resultDataAssert = cacheData.resultDataAssert(); // 断言实际的请求的返回值
		Class cacheDataAssert = cacheData.cacheDataAssert(); // 断言缓存
		String remark = cacheData.remark();
		String id = cacheData.id();

		Object[] arguments = methodInvocation.getArguments();
		String isolationSignal = dataHelper.threadLocal.get(); // 隔离标记

		String className = methodInvocation.getThis().getClass().getName();
		String methodName = method.getName();

		DataHelper.ActualDataFunctional actualDataFunctional = new DataHelper.ActualDataFunctional() {
			@Autowired
			public DataHelper.ActualDataModel getActualData() throws Throwable {

				DataHelper.ActualDataModel dataModel;
				Object data = null;
				boolean assertSucceeded = true; // 断言成功
				boolean throwException = false; // 抛出异常
				Throwable throwable = null;
				try {
					data = methodInvocation.proceed();
					dataModel = new DataHelper.ActualDataModel();
					dataModel.setData(data);
					dataModel.setExpirationTime(expirationTime(expiration, behindExpiration, capitalExpiration));
					dataModel.setSucceeded(assertSucceeded = dataHelper.doResultDataAssert(data, resultDataAssert));
					return dataModel;
				} catch (Throwable t) {
					t.printStackTrace();
					throwException = true;
					throwable = t;
					throw t;
				} finally {
					if (throwException) {
						// 实际请求发生异常
						logger.info(String.format(	"\n ************* CacheData *************" +
													"\n ** -------- 实际请求发生异常 ------- **" +
													"\n ** 执行类名：%s" +
													"\n ** 执行方法：%s" +
													"\n ** 方法入参：%s" +
													"\n ** 异常信息：%s" +
													"\n *************************************",
								className,
								methodName,
								Arrays.toString(arguments),
								throwable + DataHelper.printStackTrace(throwable.getStackTrace())));
					} else {
						if (assertSucceeded) {
							// 实际请求成功
							dataHelper.log(String.format(	"\n ************* CacheData *************" +
															"\n ** --------- 实际请求成功 --------- **" +
															"\n ** 执行类名：%s" +
															"\n ** 执行方法：%s" +
															"\n ** 方法入参：%s" +
															"\n ** 返回数据：%s" +
															"\n *************************************",
									className,
									methodName,
									Arrays.toString(arguments),
									data),
									methodcacheProperties,
									logger);
						} else {
							// 断言请求发生异常
							logger.warn(String.format(	"\n ************* CacheData *************" +
														"\n ** -------- 断言请求异常 ------- **" +
														"\n ** 执行类名：%s" +
														"\n ** 执行方法：%s" +
														"\n ** 方法入参：%s" +
														"\n ** 返回数据：%s" +
														"\n *************************************",
									className,
									methodName,
									Arrays.toString(arguments),
									data));
						}
					}
				}
			}
		};

		CacheDataModel cacheDataModel = dataHelper.getData(proxy, methodInvocation, isolationSignal, refresh,
				actualDataFunctional, id, remark, nullable, shared, cacheDataAssert);

		Object data = cacheDataModel.getData();

		dataHelper.log(String.format(	"\n ************* CacheData *************" +
										"\n ** --------- 获取数据成功 --------- **" +
										"\n ** 执行类名：%s" +
										"\n ** 执行方法：%s" +
										"\n ** 方法入参：%s" +
										"\n ** 返回数据：%s" +
										"\n *************************************",
				className,
				methodName,
				Arrays.toString(arguments),
				data),
				methodcacheProperties,
				logger);

		return cacheDataModel.getData();
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
