package love.kill.methodcache.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.*;
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
	 * 查询数据
	 * @param key 键
	 * @return 值
	 */
	public Object get(String key) {
		return key == null ? null : redisTemplate.opsForValue().get(key);
	}

	/**
	 * 插入数据
	 * @param key   键
	 * @param value 值
	 */
	@SuppressWarnings("unchecked")
	public void set(String key, Object value) {
		try {
			redisTemplate.opsForValue().set(key, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 插入数据并设置过期时间
	 *
	 * @param key   键
	 * @param value 值
	 * @param timeout 超时(毫秒)，小于等于0设置无限期
	 */
	@SuppressWarnings("unchecked")
	public void set(String key, Object value, long timeout) {
		try {
			if (timeout > 0) {
				redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.MILLISECONDS);
			} else {
				set(key, value);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 查询key
	 * @param pattern 匹配值
	 * @return 匹配的key
	 */
	@SuppressWarnings("unchecked")
	public Set<String> keys(String pattern) {
		if(StringUtils.isEmpty(pattern)){
			pattern = "*";
		}
		try {
			return redisTemplate.keys(pattern);
		}catch (Exception e){
			e.printStackTrace();
			return new HashSet<>();
		}

	}

	/**
	 * 删除数据
	 * @param key 键
	 * @return 删除成功
	 */
	@SuppressWarnings("unchecked")
	public boolean del(String key) {
		try {
			Boolean b = redisTemplate.delete(key);
			if(b == null){
				return false;
			}
			return b;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 加锁
	 * @param key key值
	 * @return 加锁成功
	 */
	public boolean lock(String key) {
		return RedisLockUtil.lock(redisTemplate,key,getLockValue(key),30 * 1000);
	}

	/**
	 * 解锁
	 * @param key key值
	 * @return 解锁成功
	 */
	public boolean unlock(String key) {
		return RedisLockUtil.unlock(redisTemplate,key,getLockValue(key));
	}

	/**
	 * 获取锁内容
	 * @param key
	 * @return 值
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
