package com.meituan.mq.ps;

import com.meituan.mq.simple.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by wangbin on 2018/6/26.
 */
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
