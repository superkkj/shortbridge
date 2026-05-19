package com.shortbridge.publish.worker;

import com.shortbridge.publish.dto.PublishMessage;
import com.shortbridge.support.rabbitmq.QueueNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublishWorker {

  private final PublishProcessor processor;

  @RabbitListener(queues = QueueNames.YOUTUBE)
  public void handleYoutube(PublishMessage message) {
    log.info("received publish message: queue={} target={}", QueueNames.YOUTUBE, message.postTargetId());
    processor.process(message);
  }

  @RabbitListener(queues = QueueNames.INSTAGRAM)
  public void handleInstagram(PublishMessage message) {
    log.info("received publish message: queue={} target={}", QueueNames.INSTAGRAM, message.postTargetId());
    processor.process(message);
  }

  @RabbitListener(queues = QueueNames.TIKTOK)
  public void handleTiktok(PublishMessage message) {
    log.info("received publish message: queue={} target={}", QueueNames.TIKTOK, message.postTargetId());
    processor.process(message);
  }
}
