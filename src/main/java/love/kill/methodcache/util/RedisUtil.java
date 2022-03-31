package love.kill.methodcache.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public class RedisUtil {

	final private static ThreadLocal<ConcurrentHashMap<String,String>> threadLocal = new ThreadLocal<>();

	private RedisTemplate redisTemplate;

	public RedisUtil(RedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * 获取缓存
	 *
	 * @param key 键
	 * @return 值
	 */
	public Object get(String key) {
		return key == null ? null : redisTemplate.opsForValue().get(key);
	}


	/**
	 * 添加缓存
	 *
	 * @param key   键
	 * @param value 值
	 * @return true成功 false失败
	 */
	@SuppressWarnings("unchecked")
	public boolean set(String key, Object value) {
		try {
			redisTemplate.opsForValue().set(key, value);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 添加缓存并设置过期时间
	 *
	 * @param key   键
	 * @param value 值
	 * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
	 * @return true成功 false 失败
	 */
	@SuppressWarnings("unchecked")
	public boolean set(String key, Object value, long time) {
		try {
			if (time > 0) {
				redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
			} else {
				set(key, value);
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 加锁
	 * @param key key值
	 * @return 是否获取到
	 */
	public boolean lock(String key) {
		return RedisLockUtil.lock(redisTemplate,key,getLockValue(key),30 * 1000);
	}

	/**
	 * 解锁
	 * @param key key值
	 * @return 是否获取到
	 */
	public boolean unlock(String key) {
		return RedisLockUtil.unlock(redisTemplate,key,getLockValue(key));
	}

	/**
	 * 获取锁内容
	 * */
	private String getLockValue(String key){
		ConcurrentHashMap<String,String> kvMap  = threadLocal.get();
		String value = null;
		if(kvMap == null){
			kvMap = new ConcurrentHashMap<>();
		}else {
			value = kvMap.get(key);
		}

		if(value==null || StringUtils.isEmpty(value)){
			long threadId = Thread.currentThread().getId();
			value = UUID.randomUUID().toString() + "_" + String. valueOf(threadId);
			kvMap.put(key,value);
			threadLocal.set(kvMap);
		}
		return value;
	}
}
