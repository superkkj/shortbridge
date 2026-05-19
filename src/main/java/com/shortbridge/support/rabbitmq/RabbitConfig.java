package com.shortbridge.support.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String EXCHANGE = "publish.exchange";
  public static final String DLX = "publish.dlx";

  @Bean
  public DirectExchange publishExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(DLX, true, false);
  }

  @Bean
  public Queue youtubeQueue() {
    return queue(QueueNames.YOUTUBE);
  }

  @Bean
  public Queue instagramQueue() {
    return queue(QueueNames.INSTAGRAM);
  }

  @Bean
  public Queue tiktokQueue() {
    return queue(QueueNames.TIKTOK);
  }

  @Bean
  public Queue youtubeDlq() {
    return new Queue(QueueNames.dlqOf(QueueNames.YOUTUBE), true);
  }

  @Bean
  public Queue instagramDlq() {
    return new Queue(QueueNames.dlqOf(QueueNames.INSTAGRAM), true);
  }

  @Bean
  public Queue tiktokDlq() {
    return new Queue(QueueNames.dlqOf(QueueNames.TIKTOK), true);
  }

  @Bean
  public Binding youtubeBinding() {
    return binding(youtubeQueue(), QueueNames.YOUTUBE);
  }

  @Bean
  public Binding instagramBinding() {
    return binding(instagramQueue(), QueueNames.INSTAGRAM);
  }

  @Bean
  public Binding tiktokBinding() {
    return binding(tiktokQueue(), QueueNames.TIKTOK);
  }

  @Bean
  public Binding youtubeDlqBinding() {
    return BindingBuilder.bind(youtubeDlq()).to(deadLetterExchange()).with(QueueNames.YOUTUBE);
  }

  @Bean
  public Binding instagramDlqBinding() {
    return BindingBuilder.bind(instagramDlq()).to(deadLetterExchange()).with(QueueNames.INSTAGRAM);
  }

  @Bean
  public Binding tiktokDlqBinding() {
    return BindingBuilder.bind(tiktokDlq()).to(deadLetterExchange()).with(QueueNames.TIKTOK);
  }

  @Bean
  public MessageConverter messageConverter() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    return new Jackson2JsonMessageConverter(mapper);
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory factory, MessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(factory);
    template.setMessageConverter(converter);
    template.setExchange(EXCHANGE);
    return template;
  }

  private static Queue queue(String name) {
    return QueueBuilder.durable(name)
        .withArgument("x-dead-letter-exchange", DLX)
        .withArgument("x-dead-letter-routing-key", name)
        .build();
  }

  private Binding binding(Queue queue, String routingKey) {
    return BindingBuilder.bind(queue).to(publishExchange()).with(routingKey);
  }
}
