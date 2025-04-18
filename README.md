### 一、什么是 MethodCache

&emsp;&emsp;**MethodCache**是一个专为 Spring Boot 项目设计的轻量级**方法级缓存**框架，支持 “内存” 和 “Redis” 两种缓存存储介质。它提供了丰富的注解，如 @CacheData 用于缓存方法返回值，@DeleteData 用于清除指定标识的缓存数据。同时，项目还具备完善的统计功能，可查看缓存的命中情况、耗时统计以及异常信息等。


### 二、MethodCache 能做什么

#### 减少重复调用：
&emsp;&emsp;微服务之间、服务内的方法之间都存在调用关系，这个微服务和服务内的方法之间可能会存在重复的调用。假设【方法A】调用【方法B】和【方法C】，如果【方法B】也调用了【方法C】，那么就会导致【方法C】被调用了2次。
&emsp;&emsp;早期，我们通过接口规划与代码重构来避免此类问题。随着业务发展，微服务在增多，服务的代码量越来越大，各服务之间的调用也愈加错综复杂。致使我们不得不花费更多的时间进行接口规划与代码重构，每次代码重构又可能引发其他更多问题。如：【方法A】和【方法B】需要调用同一个下游服务的方法【方法C】。在业务调整后，【方法A/B】被调整到同一次业务请求(同一个页面的两个请求)，导致【方法C】被调用两次。此时需要对这【方法A】和【方法B】进行代码重构。<u>*如果【方法A/B】又作为下游被调用，那么重构成本将以指数型在增加*</u>，显然这不是一个好的方案。
&emsp;&emsp;经过反复思考，我们决定变换思路：<font color=red>允许代码上的"重复"调用</font>。在<u>业务允许的时间</u>内对方法的返回值进行临时缓存，根据入参命中返回缓存中的数据，<font color=red>而非对发起重复的调用</font>。

#### 静态数据缓存：
&emsp;&emsp;项目中往往会存在一个或多个不经常更新的数据。例如banner推广、文章等等。每次用户打开页面，都会对数据库发起一次读请求。我们可以通过缓存方式存储下来，但这些数据更新频率并不是固定的，普通的缓存方案会导致数据更新后，存在一定程度的延迟问题。  
&emsp;&emsp;我们需要支持“清除”功能的缓存方案：第一位用户查询时，将数据缓存起来。当配置对应的内容(banner推广、文章等等)后，会自动清除这个内容对应的缓存，下一位用户请求时，就能重新将新数据放入缓存中。


### 三、如何使用 MethodCache

1、引入jar包
    
    <dependency>
        <groupId>love.kill</groupId>
        <artifactId>methodcache-spring-boot-starter</artifactId>
        <version>2.1.0</version>
    </dependency>

2、在配置(application.yml)中开启缓存

    methodcache:
        enable: true

3、在接口或实现类方法上加上 **@CacheData** 注解
    
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
    7、shared：共享式缓存数据。
    8、resultDataAssert：(实际的)请求返回值断言，断言为true则缓存当前返回值，为false则不进行缓存。
    9、cacheDataAssert：断言缓存数据，断言为true则返回缓存数据，为false则放弃当前缓存并重新发起请求。
    10、remark：缓存备注。


### 五、@CacheIsolation 属性说明

    1、isolationStrategy：隔离级别，'N'表示不隔离，'T'表示线程隔离，默认 N。开启线程隔离后，该方法(及后续调用方法)的缓存数据，仅对产生该缓存数据的线程可见。


### 六、@DeleteData 属性说明

    1、id：缓存标识。方法执行成功后，删除指定标识对应的缓存数据。


### 七、项目配置说明

    # 方法缓存
    methodcache:
      # 缓存应用名称，若为空则取 ${spring.application.name}
      name: demo-for-methodcache
      # 分组名，同一分组下的缓存相互可见
      groupName: demo-groupname-for-methodcache
      # 开启缓存。true：开启，false(默认)：关闭。
      enable: true
      # 缓存方式。(M)emory：内存，(R)edis：redis，默认 M
      cache-type: R
      # Redis配置（仅缓存方式cache-type为Redis时生效)
      redis:
        database: 1
        host: 127.0.0.1
        port: 6378
      # 输出日志(info级别)。true：开启，false(默认)：关闭
      enable-log: true
      # 开启端点信息，默认false
      enable-endpoint: true
      # 内存监控，默认true（仅内存缓存方式生效）
      enable-memory-monitor: true
      # 内存告警阈值，百分比，取值范围：(0, 100)，默认：50（仅内存缓存方式生效）
      memory-threshold: 50
      # GC阈值，百分比，取值范围：(0, 100)，默认：50（仅内存缓存方式生效）
      gc-threshold: 50
      # 统计
      statistics:
        # 开启统计，默认false
        enable: true
        # 分组名。缓存方式为Redis时生效，同Redis(host/port/database)下同分组统计互相可见。
        group-name: CustomerServer


### 八、DEMO

    内存方式：https://github.com/Lycop/demo-for-methodcache-with-memory.git  
    Redis方式：https://github.com/Lycop/demo-for-methodcache-with-redis.git


### 九、API

#### 1、查看缓存
    【地址】：/method/cache
    【方法】：GET
    【入参】：
        match：模糊匹配，非必传。支持“方法签名”、“缓存ID”、“缓存哈希值”
        pageSize：每页大小，默认20。
        pageNo：当前页数，默认1。
    【出参】：
        args：请求入参
        data：缓存数据
        hashCode：缓存哈希值
        cacheTime：缓存数据时间
        expireTime：缓存数据过期时间


#### 2、清除指定缓存
    【地址】：/method/cache
    【方法】：DELETE
    【入参】：
        id：缓存ID
        hashcode：缓存哈希值
    【出参】：已删除的缓存数据


#### 3、清除所有缓存
    【地址】：/method/cache/all
    【方法】：DELETE
    【入参】：无
    【出参】：已删除的缓存数据

#### 4、查看统计信息
    【地址】：/method/cache/statistics
    【方法】：GET
    【入参】：
        match：模糊匹配，非必传。支持“方法签名”、“缓存ID”
        orderBy：排序，0-id，1-总次数，2-命中次数，3-未命中次数，4-命中时平均耗时，5-未命中时平均耗时
        orderType：排序方式，0-升序，1-降序
    【出参】：
        id：缓存ID
        remark：缓存备注
        times：请求总次数
        hit：缓存命中次数
        avgOfHitSpend：缓存命中的平均耗时
        totalOfHitSpend：缓存命中的总耗时
        minHitSpend：缓存命中最小耗时
        timeOfMinHitSpend：缓存命中最小耗时发生的时间
        argsOfMinHitSpend：缓存命中最小耗时的请求参数
        maxHitSpend：缓存命中最大耗时
        timeOfMaxHitSpend：缓存命中最大耗时发生的时间
        argsOfMaxHitSpend：缓存命中最大耗时的请求参数
        failure：缓存未命中
        avgOfFailureSpend：缓存未命中的平均耗时
        totalOfFailureSpend：缓存未命中的总耗时
        minFailureSpend：缓存未命中最小耗时
        timeOfMinFailureSpend：缓存未命中最小耗时发生的时间
        argsOfMinFailureSpend：缓存未命中最小耗时的请求参数
        maxFailureSpend：缓存未命中最大耗时
        timeOfMaxFailureSpend：缓存未命中最大耗时发生的时间
        argsOfMaxFailureSpend：缓存命中最大耗时的请求参数
        exception：方法发生异常次数
        argsOfLastException：方法发生异常的请求参数
        stackTraceOfLastException：最近一次发生异常信息栈
        timeOfLastException：最近一次发生异常时间

#### 5、清空指定统计信息
    【地址】：/method/cache/statistics
    【方法】：DELETE
    【入参】：
        id：缓存ID
        method：方法签名
    【出参】：已清空的统计信息

#### 6、清空所有缓存统计
    【地址】：/method/cache/statistics/all
    【方法】：DELETE
    【入参】：无
    【出参】：已清空的统计信息


### 十、缓存存储介质

&emsp;&emsp;**MethodCache**支持“内存”和“Redis”两种方式作为缓存的存储介质，默认使用“内存“方式。  

#### 1、内存方式
    methodcache:
      cache-type: M

&emsp;&emsp;内存方式不需要很多的配置和额外的环境，就可以将**MethodCache**快速集成到您的项目中。  
&emsp;&emsp;使用内存作为缓存存储介质，方法的返回值和统计数据均会被保存在内存中。这就意味着当您的应用重启后，缓存的数据和统计数据将会消失。如果您对这些数据很重视，那么建议使用Redis方式。

#### 2、Redis方式(推荐)
    methodcache:
      cache-type: R

&emsp;&emsp;当选择Redis作为缓存存储介质，方法的返回值和统计数据将会被存储到Redis中。如果这个返回值是一个自定义的对象，请确保这个对象是可序列化的(Serializable)，否则可能会报错：<font color=red>NotSerializableException</font>。


### 十一、运行环境
    Java 8+
    Spring Boot 2.x 及以上


## 更新日志

#### 2.0.1(2022/09/06)
    支持缓存统计；
    支持内存回收(内存缓存模式)。

#### 2.0.2(2022/09/16)
    支持缓存删除(@DeleteData)；
    优化统计信息查询速度；
    修复统计耗时不准确问题。

#### 2.0.3(2023/07/12)
    支持隔离策略：缓存数据可见范围。N(one，默认)表示不隔离；T(hread)表示线程隔离，数据仅对缓存时的线程可见；
    修复BUG：高并发场景下，使用内存缓存方式，在移除过期数据时可能会出现ConcurrentModificationException异常。

#### 2.0.4(2023/07/31)
    优化支持隔离策略；
    支持Feign、MyBatis等声明式调用数据缓存；
    异步方式缓存数据，提高返回速度。

#### 2.0.5(2023/08/22)
    支持共享式缓存数据;
    修复方法的入参数为Map和Collection的实现类时，哈希值判定异常问题。

#### 2.0.6(2023/11/16)
    增加入参哈希复杂度；
    Redis 存储的模式下，支持配置 Redis 锁超时时间(默认30秒)；
    修复BUG：@CacheData 开启 "nullable" 时，可能会返回 ClassCastException 异常。

#### 2.1.0(2025/03/06)
    支持分组；
    支持请求结果断言；
    Redis调整为独立的连接，避免影响业务Redis。
