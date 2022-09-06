### 一、什么是MethodCache

MethodCache是一个基于SpringBoot的非侵入式**方法结果缓存**开源组件。根据入参对指定方法返回的返回值进行缓存，下次对以同样的入参进行请求时，在规定的数据有效期内会返回缓存数据。


### 二、为什么要开发MethodCache

#### 减少重复调用：
&emsp;&emsp;为了确保响应速度，及降低服务器压力，我们要尽可能减少服务之间、方法之间不必要及重复的调用。但是这通常需要花费大量的人力。  
&emsp;&emsp;早期，我们通过接口规划与代码重构来避免此类问题。随着业务发展服务在增多，服务的代码量越来越大，各服务的方法之间的调用与层级也愈加错综复杂。我们不得不花费更多的时间进行接口规划与代码重构，每次代码重构又可能引发其他更多问题。如：【方法A】和【方法B】需要调用同一个下游服务的方法【方法C】。在业务调整后，【方法A/B】被调整到同一次业务请求(同一个页面的两个请求)，导致【方法C】被调用两次。此时需要对这【方法A】和【方法B】进行代码重构。<u>*如果【方法A/B】又作为下游被调用，那么重构成本将以指数型增加*</u>，显然这不是一个好的方案。  
&emsp;&emsp;经过反复思考，我们决定变换了一个思路：<font color=red>允许重复调用</font>。  
&emsp;&emsp;我们在“业务允许的时间”内对【方法A】的返回值进行缓存。在这个时间范围内，缓存命中则返回数据，<font color=red>而非对【方法A】发起重复调用</font>。

#### 静态数据缓存：
&emsp;&emsp;首页中存在多个不会经常变化的数据。例如banner区、文章、推荐等。每当用户打开页面都会对数据库发起一次读请求。这些数据更新频率约为每月一次，但并不是固定的。  
&emsp;&emsp;我们需要支持“清除”功能的缓存方案：第一个用户查询时将数据缓存起来(一个月、两个月甚至更长)，在运营同事配置对应的内容(banner、文章、推荐)后，可以清除对应内容的缓存。


### 三、快速开始

1、引入jar包
    
    <dependency>
        <groupId>love.kill</groupId>
        <artifactId>methodcache-spring-boot-starter</artifactId>
        <version>2.0.1</version>
    </dependency>

2、在 application.yml 开启

    methodcache:
        enable: true

3、在接口或实现类方法上加上 **@cacheData** 注解
    
    /**
	 * 查询用户信息
	 * */
	@CacheData(id = "getUserInfo", expiration = 10000L, refresh = false, remark = "查询用户信息")
	UserInfo getUserInfo(String userId);


### 四、@CacheData 属性说明

    1、id：缓存标识。标识一个缓存，可用于查看、清除缓存。
    2、expiration：缓存时间(默认3000毫秒)。此属性表示缓存有效时间，在有效时间内会返回缓存中的数据；过期则发起实际的请求。
    3、refresh：刷新(默认false)。缓存命中且有效时，返回缓存的数据后，异步发起请求刷新缓存数据。
    4、behindExpiration：宽限期。此属性表示缓存到期后，会在这个时间范围内随机失效，避免缓存大范围同时失效。
    5、capitalExpiration：基础时间。此属性表示在当前指定类型(秒/分钟/小时/日/月/年)内，缓存一直有效。
    6、nullable：缓存“null”(默认true)。方法返回了“null(包含异常导致)” 时，仍然缓存。
    7、remark，缓存备注。


### 五、项目配置说明

    # 方法缓存
    methodcache:
    # 缓存应用名称，若为空则取 ${spring.application.name}
    #  name: demo-for-methodcache
      # 开启缓存。true：开启，false(默认)：关闭。
      enable: true
      # 缓存方式。(M)emory：内存，(R)edis：redis，默认 M
      cache-type: R
      # 输出日志(info级别)。true：开启，false(默认)：关闭
      enable-log: true
      # 开启端点信息，默认false
      enable-endpoint: true
      # 开启统计，默认false
      enable-statistics: true
      # 内存监控，默认true（仅内存缓存方式生效）
      enable-memory-monitor: true
      # 内存告警阈值，百分比，取值范围：(0, 100)，默认：50（仅内存缓存方式生效）
      memory-threshold: 50
      # GC阈值，百分比，取值范围：(0, 100)，默认：50（仅内存缓存方式生效）
      gc-threshold: 50

    # 其他配置
    spring:
        # Redis相关配(仅Redis缓存方式生效)
        redis:
            database: 1
            host: 127.0.0.1
            port: 6378


### 六、DEMO

    内存方式：https://github.com/Lycop/demo-for-methodcache-with-memory.git  
    Redis方式：https://github.com/Lycop/demo-for-methodcache-with-redis.git


### 七、API

#### 1、查看缓存
    URL：/methodcache/cache
    方法：GET
    参数：
        【match】：模糊匹配，支持“方法签名”、“缓存ID”、“缓存哈希值”

#### 2、清除指定缓存
    URL：/methodcache/cache
    方法：DELETE
    参数：
        【id】：缓存ID
        【hashcode】：缓存哈希值

#### 3、清除所有缓存
    URL：/methodcache/cache/all
    方法：DELETE
    参数：无

#### 4、查看统计信息
    URL：/methodcache/statistics
    方法：GET
    参数：
        【match】：模糊匹配，支持“方法签名”、“缓存ID”
        【order_by】：排序，0-id，1-总次数，2-命中次数，3-未命中次数，4-命中时平均耗时，5-未命中时平均耗时
        【order_type】：排序方式，0-升序，1-降序

#### 5、清空指定统计信息
    URL：/methodcache/statistics
    方法：DELETE
    参数：
        【id】：缓存ID
        【method】：方法签名

#### 6、清空所有缓存统计
    URL：/methodcache/statistics/all
    方法：DELETE
    参数：无


### 八、缓存存储介质

&emsp;&emsp;MethodCache支持“内存”和“Redis”作为缓存存储介质(默认情况下使用“内存“方式)。  

#### 1、内存方式
    methodcache:
      cache-type: M

&emsp;&emsp;内存方式不需要更多的配置和环境，就可以将**MethodCache**快速集成到您的项目中。  
&emsp;&emsp;使用内存方式，缓存数据和统计数据均会被保存在内存中。这就意味着当您的应用重启后，缓存的数据和统计数据将会消失。如果您对这些数据很重视，那么建议使用Redis方式。

#### 2、Redis方式(推荐)

    methodcache:
      cache-type: R
    
&emsp;&emsp;**MethodCache**使用*RedisTemplate*作为操作Redis的工具。因此，需要指定RedisTemplate相关配置。  
&emsp;&emsp;当选择Redis作为缓存介质，返回的数据将会被存储到Redis中。如果这个返回值是一个自定义的对象，那么这个对象应该是可序列化的(Serializable)，否则可能会报错：<font color=red>NotSerializableException</font>。


### 九、注意事项

    1、不建议在不支持幂等性的请求方法中使用。如：POST请求


### 最后

&emsp;&emsp;使用过程中，如果有问题或者建议，欢迎联系我(i@kill.love)。也欢迎大家一起加入并完善本项目。 ：）



## 更新日志

#### 2.0.1
    支持缓存统计  
    支持内存回收(内存缓存模式)