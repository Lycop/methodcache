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
	 * 获取哈希数据
	 *
	 * @param key 键
	 * @param field 字段
	 * @return 值
	 */
	@SuppressWarnings("unchecked")
	public Object hget(String key, String field) {
		try {
			return (key == null || field == null) ? null : redisTemplate.opsForHash().get(key, field);
		}catch (Exception e){
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 保存hash缓存
	 *
	 * @param key 键
	 * @param field 字段
	 * @param value 值
	 */
	@SuppressWarnings("unchecked")
	public void hset(String key, String field, String value) {
		try {
			redisTemplate.opsForHash().put(key, field, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 获取hash所有值
	 *
	 * @param key 键
	 * @return 值
	 */
	@SuppressWarnings("unchecked")
	public List<Object> hValues(String key) {
		try {
			return key == null ? null : redisTemplate.opsForHash().values(key);
		}catch (Exception e){
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 加锁
	 * @param key 键
	 * @param block 阻塞方式
	 * @return 加锁成功
	 */
	public boolean lock(String key, boolean block) throws InterruptedException {
		if(block){
			while (!lock(key)) {
				if(Thread.currentThread().isInterrupted()){
					throw new InterruptedException();
				}
			}
			return true;
		}
		return lock(key);
	}

	/**
	 * 加锁
	 * @param key 键
	 * @return 加锁成功
	 */
	public boolean lock(String key) {
		return RedisLockUtil.lock(redisTemplate,key,getLockValue(key),30 * 1000);
	}

	/**
	 * 解锁
	 * @param key 键
	 * @return 解锁成功
	 */
	public boolean unlock(String key) {
		return RedisLockUtil.unlock(redisTemplate,key,getLockValue(key));
	}

	/**
	 * 获取锁内容
	 *
	 * @param key 键
	 * @return 值
	 */
	private String getLockValue(String key) {
		ConcurrentHashMap<String, String> kvMap = threadLocal.get();
		if (kvMap == null) {
			kvMap = new ConcurrentHashMap<>();
			threadLocal.set(kvMap);
		}

		String value = kvMap.get(key);
		if (value == null || StringUtils.isEmpty(value)) {
			value = UUID.randomUUID().toString() + "@" + String.valueOf(Thread.currentThread().getId());
			kvMap.put(key, value);
		}
		return value;
	}
}
