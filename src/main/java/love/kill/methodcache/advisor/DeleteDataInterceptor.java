package love.kill.methodcache.advisor;

import love.kill.methodcache.annotation.DeleteData;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.ThreadPoolBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

/**
 * DeleteData 拦截通知
 * 方法执行完毕后，删除指定的缓存
 *
 * @author Lycop
 */
public class DeleteDataInterceptor implements MethodInterceptor {

	/**
	 * 数据Helper
	 */
	private DataHelper dataHelper;

	/**
	 * 清除缓存线程池
	 */
	private ExecutorService deleteCacheExecutorService = ThreadPoolBuilder.buildDefaultThreadPool();

	public DeleteDataInterceptor(DataHelper dataHelper) {
		this.dataHelper = dataHelper;
	}

	@Override
	public Object invoke(MethodInvocation methodInvocation) throws Throwable {

		Method method = methodInvocation.getMethod();
		DeleteData deleteData = method.getAnnotation(DeleteData.class);

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
