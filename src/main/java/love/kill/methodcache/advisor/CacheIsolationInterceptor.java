package love.kill.methodcache.advisor;

import love.kill.methodcache.constant.IsolationStrategy;
import love.kill.methodcache.datahelper.DataHelper;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheIsolation 拦截通知
 *
 * @author Lycop
 */
public class CacheIsolationInterceptor implements MethodInterceptor {


	private DataHelper dataHelper;

	/**
	 * 隔离策略
	 * N：不隔离，T：线程隔离，默认 N
	 */
	private Map<Class<?>, Character> isolationStrategyMap = new ConcurrentHashMap<>();

	public CacheIsolationInterceptor( DataHelper dataHelper) {
		this.dataHelper = dataHelper;
	}


	public Map<Class<?>, Character> getIsolationStrategyMap() {
		return isolationStrategyMap;
	}


	@Override
	public Object invoke(MethodInvocation methodInvocation) throws Throwable {

		Class<?> declaringClass = methodInvocation.getMethod().getDeclaringClass();
		Character strategy = isolationStrategyMap.get(declaringClass);
		if (strategy != null && IsolationStrategy.THREAD == isolationStrategyMap.get(declaringClass)) {
			String isolationSignal = null; // 隔离标记
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
