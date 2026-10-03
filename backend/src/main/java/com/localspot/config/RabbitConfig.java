package com.localspot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology RabbitMQ (khai báo tự động khi ứng dụng khởi động).
 *
 * <pre>
 * localspot.tasks (direct) ──mail.send──▶ mail.send ──(hết retry, reject)──▶ localspot.dlx ──▶ mail.send.dlq
 * </pre>
 *
 * Retry nằm ở listener ({@code spring.rabbitmq.listener.simple.retry}); message lỗi sau lượt cuối được giữ lại ở DLQ để
 * xem và chạy lại bằng tay (Management UI) thay vì mất hẳn — NFR-13.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitConfig {

    public static final String TASKS_EXCHANGE = "localspot.tasks";
    public static final String DEAD_LETTER_EXCHANGE = "localspot.dlx";
    public static final String MAIL_QUEUE = "mail.send";
    public static final String MAIL_ROUTING_KEY = "mail.send";
    public static final String MAIL_DLQ = "mail.send.dlq";

    @Bean
    DirectExchange tasksExchange() {
        return new DirectExchange(TASKS_EXCHANGE);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue mailQueue() {
        return QueueBuilder.durable(MAIL_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MAIL_DLQ)
                .build();
    }

    @Bean
    Queue mailDeadLetterQueue() {
        return QueueBuilder.durable(MAIL_DLQ).build();
    }

    @Bean
    Binding mailBinding(Queue mailQueue, DirectExchange tasksExchange) {
        return BindingBuilder.bind(mailQueue).to(tasksExchange).with(MAIL_ROUTING_KEY);
    }

    @Bean
    Binding mailDeadLetterBinding(Queue mailDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(mailDeadLetterQueue).to(deadLetterExchange).with(MAIL_DLQ);
    }

    /** Message dạng JSON (đọc được trong Management UI) thay vì Java serialization mặc định. */
    @Bean
    MessageConverter amqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
