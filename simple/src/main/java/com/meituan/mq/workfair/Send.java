package com.meituan.mq.workfair;

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
