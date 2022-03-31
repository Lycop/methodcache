package love.kill.methodcache.aspect;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.CacheData;
import love.kill.methodcache.util.DataHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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
		Method curMethod = joinPoint.getTarget().getClass().getMethod(methodSignature.getName(), methodSignature.getParameterTypes());
		CacheData cacheData = curMethod.getAnnotation(CacheData.class);

		Object resultObject;

		try {
			boolean refresh = cacheData.refresh();
			long expiration = cacheData.expiration();


			Integer argsHashCode = getArgsHashCode(args); // 入参 hash code
			String curMethodSignature = curMethod.toGenericString(); // 方法签名

			resultObject = dataHelper.getData(curMethodSignature + argsHashCode,refresh,new DataHelper.ActualDataFunctional(){
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
					return expiration;
				}
			});

			return resultObject;


		}catch (Exception e){
			logger.error("数据缓存出现异常：" + e.getMessage());
			joinPoint.proceed(args);
		}

		return joinPoint.proceed(args);
	}

	private Integer getArgsHashCode(Object[] args) throws IllegalAccessException {

		Map<String,Integer> fieldHash = new LinkedHashMap<>();

		for(int i = 0;i < args.length;i++){
			Object arg = args[i];
			fieldHash.put("arg" + i,doGetHash(arg));

		}
		return Objects.hash(fieldHash);
	}

	private int doGetHash(Object arg) throws IllegalAccessException {

		if(arg == null){
			return 0;
		}

		if(isPrimitive(arg.getClass())){
			// 基本数据类型
			return Objects.hash(arg);

		}else {
			// 复杂对象类型
			Field[] declaredFields = arg.getClass().getDeclaredFields();

			for(Field field : declaredFields){
				field.setAccessible(true);
			}

			List<Field> fieldList = new LinkedList<>(Arrays.asList(declaredFields));

			// 按属性排序
			fieldList.sort(Comparator.comparing(Field::toString));

			Map<String,Integer> fieldHash = new LinkedHashMap<>();
			for(Field field : fieldList){
				fieldHash.put(field.toString(),doGetFieldHash(field,arg));
			}
			return Objects.hash(fieldHash);
		}
	}


	private int doGetFieldHash(Field field,Object object) throws IllegalAccessException {

		if(object == null){
			return 0;
		}

		Class<?> typeClass = field.getType();
		Object fieldObject = field.get(object);


		if(isPrimitive(typeClass) || (typeClass.isAssignableFrom(Collection.class))){
			// 基本数据类型
			return Objects.hash(fieldObject);

		} else {
			// 复杂对象类型
			Field[] declaredFields = typeClass.getDeclaredFields();

			for(Field innerField : declaredFields){
				innerField.setAccessible(true);
			}

			List<Field> fieldList = new LinkedList<>(Arrays.asList(declaredFields));

			// 按属性排序
			fieldList.sort(Comparator.comparing(Field::toString));


			Map<String,Integer> fieldHash = new LinkedHashMap<>();
			for(Field innerField : fieldList){
				fieldHash.put(innerField.toString(),doGetFieldHash(innerField,fieldObject));
			}
			return Objects.hash(fieldHash);
		}
	}


	private boolean isPrimitive(Class clazz) {
		return  clazz.isPrimitive() || isInternal(clazz);
	}

	private boolean isInternal(Class clazz) {
		return  (Map.class == clazz) ||
				(List.class == clazz) ||
				(String.class == clazz) ||
				(Short.class == clazz) ||
				(Integer.class == clazz) ||
				(Long.class == clazz) ||
				(Float.class == clazz) ||
				(Double.class == clazz) ||
				(Character.class == clazz) ||
				(Boolean.class == clazz);
	}

}
