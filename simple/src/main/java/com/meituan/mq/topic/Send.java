package com.meituan.mq.topic;


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
