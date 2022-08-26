**一、什么是MethodCache**

    MethodCache是一个基于SpringBoot的、非侵入式的方法缓存开源组件。根据入参对指定方法返回的数据进行缓存，下次对同一个方法以同样的入参进行请求时，在数据有效期内会立即返回缓存数据，并异步发起实际请求并更新缓存内的数据。


**二、为什么要开发MethodCache(应用场景)**
    热点数据缓存：


    减少重复调用：
    为了确保响应速度，我们要尽可能减少服务之间、方法之间的相互调用次数。如：两个方法独立的方法需要调用同一个下游服务方法A。在某次业务调整后，这两个被调整到同一次业务请求(如同一个页面的两个请求)，那么方法A就被会调用两次。如果这两次请求的参数是一致的(大概率也是一致的)，那么第二次请求无疑冗余的。我们通过接口规划/重构和code review来避免此类问题。项目早期，业务不是特别复杂，代码较容易把控。随着业务发展，代码逐渐变多，服务也越来越多，方法之间相互调用层级关系也越来越错综复杂。我们不得不花费更多的时间进行接口规划和重构，每次重构又可能引发其他的问题，这不是一个好的方式。经过反复思考，我们变换了一个思路：允许重复调用。我们在业务允许的时间内对方法A的返回进行缓存，在这个时间范围内缓存入参如果命中，则直接返回数据，而不是对方法A的发起调用以及等待结果返回。

**二、快速开始**

    1、引入jar包
        <dependency>
            <groupId>love.kill</groupId>
            <artifactId>methodcache-spring-boot-starter</artifactId>
            <version>2.0.1</version>
        </dependency>

    2、在 application.yml 启用
        methodcache:
          enable: true

    3、在方法上加上注解 @CacheData

**三、demo**

    内存：https://github.com/Lycop/demo-for-methodcache-with-memory.git
    Redis：https://github.com/Lycop/demo-for-methodcache-with-redis.git


**四、缓存供设置的属性**

    1、expiration，缓存时间(默认3000毫秒)。此属性表示缓存有效时间，在有效时间内，数据将会返回缓存中的数据；否则发起一次请求。
    2、refresh，刷新(默认true)。方法被请求后，都会发起一次异步的请求，用于更新缓存。
    3、behindExpiration，宽限期。此属性表示缓存到期后，会在指定时间内随机失效。可以用来避免缓存大范围同时失效引起的服务器雪崩。
    4、capitalExpiration，基础时间。此属性表示在当前指定类型(秒/分钟/小时/日/月/年)内，缓存一直有效。
    5、id，缓存标识。标识一个缓存注解，可用于清除该注解的缓存。
    6、nullable，允许 null 返回值(默认true)。为 true 则表示当方法返回了 null(包含异常导致) 时，仍然缓存。
    7、remark，备注。


**五、API**

    /methodcache/cache，缓存信息
        支持方法：GET(查看)、DELETE(清除)
    /methodcache/cache/all，缓存信息
        支持方法：DELETE
    /methodcache/situation，统计
         支持方法：GET(查看)、DELETE(清除)



**六、配置文件说明**

    # 方法缓存
    methodcache:
    # 缓存应用名称，若为空则取 ${spring.application.name}
    #  name: demo-for-methodcache
      # 启用缓存。true：开启，false：关闭，默认 false
      enable: true
      # 输出日志(info级别)。true：开启，false：关闭，默认 false
      enable-log: true
      # 缓存方式。(M)emory：内存，(R)edis：redis，默认 M
      cache-type: R
      # 开启端点信息，默认关闭
      enable-endpoint: true
      # 开启统计，默认关闭
      enable-record: true



**六、不推荐使用的场景**
    1、不支持幂等性的请求。如：POST、DELETE；
    2、本次请求对结果有影响的请求；
    3、前后顺序的请求，如分页请求。



**最后**

    使用过程中，如果有问题或者建议，欢迎联系我(i@kill.love)。也欢迎大家一起加入并完善本项目。 ^ ^