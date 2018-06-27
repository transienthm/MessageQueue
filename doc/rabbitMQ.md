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
8. rabbitMQ的延迟队列

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

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Receive {
    private static final String QUEUE_NAME = "test_simple_queue";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //创建channel
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("msg receive : " + msg);
            }
        };

        channel.basicConsume(QUEUE_NAME, consumer);
    }
}
```

## 3.5 简单队列的不足

耦合性高，生产者一一对应消费者，如果需要多个消费者消费队列中的消息，此时简单队列就无能为力了。

队列名变更，源码需要同时变更

# 四、Work队列

## 4.1 模型

![image](https://user-images.githubusercontent.com/16509581/41643993-6baee62e-749f-11e8-9352-e220af0c0a1d.png)

一个生产者将消息放入队列中，可以有多个消费者进行消费

**为什么会出现工作队列？**

Simple队列：是一一对应的，实际开发中，生产者改善消息是毫不费力的，而消费者一般需要跟业务相结合，消费者接收到消息之后就需要处理，可能需要花费时间，此时队列就会积压很多消息。

## 4.2 轮询分发

生产消息

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {
    private static final String QUEUE_NAME = "test_work_queue";
    
    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //从连接中获取一个通道
        Channel channel = connection.createChannel();

        //创建队列声明
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        for (int i = 0; i < 50; i++) {
            String msg = "hello " + i;
            channel.basicPublish("", QUEUE_NAME, null, msg.getBytes());

            System.out.println("---send msg :" + msg);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        channel.close();
        connection.close();
    }
}
```

消费者1

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Recv1 {
    private static final String QUEUE_NAME = "test_work_queue";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //创建channel
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[1] msg recv1 : " + msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        boolean ack = true;
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

消费者2

```java

import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Recv2 {
    private static final String QUEUE_NAME = "test_work_queue";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //创建channel
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[2] msg recv1 : " + msg);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        boolean ack = true;
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

**现象**：

消费者1和消费者2处理的消息是一样多的，这种分发方式称为轮询分发（round-robin），不管谁忙或者谁闲，都不会多给或者少给。任务均分。

## 4.3 公平分发 fair dispatch

保证一次发送给消费者的消息不超过一条

```java
        /**
         * 每个消费者发送确认消息之前，消息队列不发送下一个消息给消费者，消费者一次只处理一个消息
         *
         * 限制发送给同一个消费者不得超过一条消息
         */
        int preFetchCount = 1;
        channel.basicQos(preFetchCount);
```



使用公平分发，必须关闭自动应答ack，改为手动

```java
channel.basicAck(envelope.getDeliveryTag(), false);
boolean ack = false;//自动应答改为false
channel.basicConsume(QUEUE_NAME, ack, consumer);
```

**生产消息**

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {
    private static final String QUEUE_NAME = "test_work_queue";
    
    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //从连接中获取一个通道
        Channel channel = connection.createChannel();

        //创建队列声明
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        /**
         * 每个消费者发送确认消息之前，消息队列不发送下一个消息给消费者，消费者一次只处理一个消息
         *
         * 限制发送给同一个消费者不得超过一条消息
         */
        int preFetchCount = 1;
        channel.basicQos(preFetchCount);

        for (int i = 0; i < 50; i++) {
            String msg = "hello " + i;
            channel.basicPublish("", QUEUE_NAME, null, msg.getBytes());

            System.out.println("---send msg :" + msg);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        channel.close();
        connection.close();
    }
}
```

消费消息

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Recv2 {
    private static final String QUEUE_NAME = "test_work_queue";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();

        //创建channel
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        channel.basicQos(1);

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[2] msg recv1 : " + msg);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean ack = false;//自动应答改为false
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

## 4.4 消息应答与消息持久化

### 4.4.1 消息应答

```java
boolean ack = false;//自动应答改为false
channel.basicConsume(QUEUE_NAME, ack, consumer);
```

ack = true时为自动确认模式，一旦rabbitMQ将消息分发给消费者，该消息就会在内存中删除；这种情况下，如果杀死正在处理消息的消费者，会丢失正在处理的消息；

ack = false时为手动回执（消息应答）模式，如果有一个消费者挂掉，就会将会给其他消费者，rabbitMQ支持消息应答，消费者发送一个消息应答，告诉rabbitMQ这个消息已经被处理，然后rabbitMQ就删除内存中的消息；

消息应答默认打开，即为false；

由于消息在内存中存储，如果rabbitMQ挂掉，消息仍然会丢失。

### 4.4.2 消息持久化

```java
boolean durable = false;
channel.queueDeclare(QUEUE_NAME, durable, false, false, null);
```

durable控制的属性就是消息的持久化。

已经声明好的队列，如果durable已经为false了，就无法修改为true，rabbitMQ不允许重新定义（不同参数）一个已存在的队列

# 五、订阅模式 Publish/Subscribe

## 5.1 模型

![image](https://user-images.githubusercontent.com/16509581/41886996-65c35916-7931-11e8-9b97-74ee5b408a3b.png)

解读：

1、一个生产者，多个消费者；

2、每个消费者都有自己的队列；

3、生产者没有直接把消息发送到队列，而是发送至交换机(eXchange)

4、每个队列都要绑定到交换机上

5、生产者发送的消息，经过交换机，到达队列，就能实现一个消息被多个消费者消费



## 5.2 实现

**生产消息**

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {
    private static final String EXCHANGE_NAME = "test_exchange_fanout";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        //声明交换机
        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

        String msg = "hello ps";

        channel.basicPublish(EXCHANGE_NAME, "", null, msg.getBytes());
        System.out.println("Send " + msg);

        channel.close();
        connection.close();
    }
}
```

![image](https://user-images.githubusercontent.com/16509581/41887423-1dafb37a-7933-11e8-8460-22f9c2a399fc.png)

消息哪去了？丢失了！因为交换机没有存储能力，在rabbitMQ中，只有队列有存储能力。此时并没有完成队列绑定到交换机，所以数据丢失了。

**消费消息**

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Recv1 {
    private static final String QUEUE_NAME = "test_ps_fanout_email";
    private static final String EXCHANGE_NAME = "test_exchange_fanout";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        //绑定队列到交换机
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "");

        channel.basicQos(1);
        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[1] msg recv1 : " + msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean ack = false;//自动应答改为false
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

不同的队列做不同的事情。

## 5.3 Exchange（交换机、转发器）

一方面接收生产者的消息，另一方面向队列推送消息

rabbitMQ提供了四种Exchange：fanout,direct,topic,header  header模式在实际使用中较少。

- fanout：不处理路由键

  ![image](https://user-images.githubusercontent.com/16509581/41888037-06c1c984-7936-11e8-88e5-82b7100391aa.png)

- direct：处理路由键

  ![image](https://user-images.githubusercontent.com/16509581/41888033-ff2e6b32-7935-11e8-83e9-dd3d45dd0f05.png)

- topic

  将路由键和某模式进行匹配

  任何发送到Topic Exchange的消息都会被转发到所有关心RouteKey中指定话题的Queue上

  ![image](https://user-images.githubusercontent.com/16509581/41897845-079689c4-795b-11e8-9e81-11a9d3608fcd.png)

# 六、路由模式

## 6.1 模型

![image](https://user-images.githubusercontent.com/16509581/41900301-fe92b86a-7960-11e8-8e2e-51bee0e666f3.png)

- 声明exchange时指定为direct模式
- 绑定队列时，指定路由键

## 6.2 实现

生产者

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {

    private static final String EXCHANGE_NAME = "test_exchange_direct";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        //声明exchange
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");

        String msg = "hello direct";

        //指定路由键
        String routingKey = "warning";
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, msg.getBytes());

        System.out.println("send msg:" + msg);
        channel.close();
        connection.close();
    }
}
```

消费者1

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Recv1 {
    private static final String EXCHANGE_NAME = "test_exchange_direct";
    private static final String QUEUE_NAME = "test_queue_direct";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        channel.basicQos(1);

        //绑定队列与交换机时，指定路由键
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "error");


        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[1] msg recv1 : " + msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean ack = false;//自动应答改为false
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

消费者2

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Recv2 {
    private static final String EXCHANGE_NAME = "test_exchange_direct";
    private static final String QUEUE_NAME = "test_queue_direct_2";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        channel.basicQos(1);

        //绑定队列与交换机时，指定路由键
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "error");
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "info");
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "warning");

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[2] msg recv2 : " + msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean ack = false;//自动应答改为false
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

# 七、Topic模式

## 7.1 模型

![img](https://www.rabbitmq.com/img/tutorials/python-five.png)

\# 匹配一个或多个

\* 匹配一个

## 7.2 实现

生产者

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {

    private static final String EXCHANGE_NAME = "test_exchange_topic";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        //声明exchange,指定模式为topic
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        String msg = "商品....";

        String routingKey = "goods.delete";
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, msg.getBytes());

        System.out.println("send msg:" + msg);
        channel.close();
        connection.close();
    }
}
```

消费者1

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by wangbin on 2018/6/26.
 */
public class Recv1 {
    private static final String EXCHANGE_NAME = "test_exchange_topic";
    private static final String QUEUE_NAME = "test_queue_topic_1";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        channel.basicQos(1);

        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "goods.#");

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[1] msg recv1 : " + msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean ack = false;//自动应答改为false
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

消费者2

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by wangbin on 2018/6/26.
 */
public class Recv2 {
    private static final String EXCHANGE_NAME = "test_exchange_topic";
    private static final String QUEUE_NAME = "test_queue_topic_2";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        channel.basicQos(1);

        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "goods.add");

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body, "utf8");
                System.out.println("[2] msg recv2 : " + msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean ack = false;//自动应答改为false
        channel.basicConsume(QUEUE_NAME, ack, consumer);
    }
}
```

其中，消费者1绑定路由键为`goods.#`，消费者2绑定路由键为`goods.add`。当生产者发送的消息路由键为`goods.add`时，两个消费者都会收到消息并处理；当生产者发送的消息路由键为`goods.update`时，只有消费者1可以接收到消息。

# 八、RabbitMQ的消息确认机制

在rabbitMQ中，可以通过持久化数据解决rabbitMQ服务器异常的数据丢失问题。

问题：生产者将消息发送出去之后，消息到底有没有到达rabbitMQ服务器；默认情况是不知道消息已到达的

两种方式：

- AMQP实现了事务机制
- confirm模式

## 8.1 事务机制

- txSelect 

  用于将当前channel设置成transaction模式

- txCommit

  用于提交事务

- txRollback

  回滚事务

生产者发送消息

```java
import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Send {
    private static final String QUEUE_NAME = "test_queue_tx";

    public static void main(String[] args) throws IOException, TimeoutException {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        String msg = "hello tx msg!";

        try {
            channel.txSelect();
            channel.basicPublish("", QUEUE_NAME, null, msg.getBytes());
            channel.txCommit();
        } catch (IOException e) {
            channel.txRollback();
            System.out.println("发生异常，事务已回滚");
        }
    }
}
```

事务机制会降低rabbitMQ的吞吐量。

## 8.2 Confirm模式

生产者将信道设置成confirm模式，一旦信道进入confirm模式，所有在该信道上面发布的消息都将会被指派一个唯一的ID(从1开始)，一旦消息被投递到所有匹配的队列之后，broker就会发送一个确认给生产者(包含消息的唯一ID)，这就使得生产者知道消息已经正确到达目的队列了，如果消息和队列是可持久化的，那么确认消息会在将消息写入磁盘之后发出，broker回传给生产者的确认消息中delivery-tag域包含了确认消息的序列号，此外broker也可以设置basic.ack的multiple域，表示到这个序列号之前的所有消息都已经得到了处理；

confirm模式最大的好处在于他是异步的，一旦发布一条消息，生产者应用程序就可以在等信道返回确认的同时继续发送下一条消息，当消息最终得到确认之后，生产者应用便可以通过回调方法来处理该确认消息，如果RabbitMQ因为自身内部错误导致消息丢失，就会发送一条nack消息，生产者应用程序同样可以在回调方法中处理该nack消息。

编程模式：

1、普通，发一条

2、批量，发一批

3、异步confirm模式，提供一个回调方法