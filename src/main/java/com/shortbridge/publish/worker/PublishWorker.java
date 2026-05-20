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

  @RabbitListener(queues = {QueueNames.YOUTUBE, QueueNames.INSTAGRAM, QueueNames.TIKTOK})
  public void handle(PublishMessage message) {
    log.info(
        "received publish message: platform={} target={}",
        message.platform(),
        message.postTargetId());
    processor.process(message);
  }
}
