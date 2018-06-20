# 一、内容大纲&使用场景

## 1. 消息队列解决了什么问题?

- 异步处理
- 应用解耦
- 流量削锋
- 日志处理
- ......

## 2. rabbitMQ安装与配置

## 3. Java操作rabbitMQ

1. simple		简单队列
	. work queues		工作队列     公平分发  轮询分发 
	. publish/subscribe	发布订阅
	. routing		路由选择    通配符模式		
	. Topics 		主题
6. 手动和自动确认消息
7. 队列的持久化和非持久化
8. rabbitmq的延迟队列

## 4. Spring AMQP  Spring-Rabbit

## 5. DEMO

1. MQ实现搜索引擎DIH增量 
2. 未支付订单30分钟 取消
3. 类似百度统计 cnzz 架构 消息队列

# 二、用户及vhost配置

## 2.1 添加用户

![image](https://user-images.githubusercontent.com/16509581/41573189-0760a708-73ae-11e8-9b35-58b24937d287.png)

## 2.2 virtual hosts管理

virtual hosts相当于mysql的db

![image](https://user-images.githubusercontent.com/16509581/41573252-5c210364-73ae-11e8-84d8-c1a6a7a52f4b.png)

一般以/开头

## 2.3 用户授权

需要对用户进行授权

![image](https://user-images.githubusercontent.com/16509581/41573315-b449ebaa-73ae-11e8-9b22-b236dfec8a86.png)

# 三、简单队列

## 3.1 模型

![image](https://user-images.githubusercontent.com/16509581/41634080-8b622fd8-7474-11e8-84d0-585f75c5c0da.png)

P：消息生产者

红色：队列

C：消息消费者

包含三个对象：生产者、队列、消费者

## 3.2 获取mq连接 

```java
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ConnectionUtil {
    /**
     * 获取MQ的连接
     * @return
     */
    public static Connection getConnection() throws IOException, TimeoutException {
        //定义一个连接工厂
        ConnectionFactory factory = new ConnectionFactory();

        factory.setHost("127.0.0.1");
        //AMQP的端口
        factory.setPort(5672);
        //vhost
        factory.setVirtualHost("/vhost_mmr");
        factory.setUsername("rabbit");
        factory.setPassword("123456");

        Connection connection = factory.newConnection();
        return connection;
    }
}
```

## 3.3 生产消息

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {
    private static final String QUEUE_NAME = "test_simple_queue";
    
    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //从连接中获取一个通道
        Channel channel = connection.createChannel();

        //创建队列声明
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        String msg = "hello world!";

        channel.basicPublish("", QUEUE_NAME, null, msg.getBytes());

        System.out.println("---send msg :" + msg);
        channel.close();
        connection.close();
    }
}
```

## 3.4 消费消息

