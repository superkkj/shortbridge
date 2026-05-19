package com.shortbridge.publish;

import com.shortbridge.common.util.IdempotencyKeys;
import com.shortbridge.platform.publishjob.service.PublishJobCommandService;
import com.shortbridge.publish.dto.EnqueueRequest;
import com.shortbridge.publish.dto.PublishMessage;
import com.shortbridge.support.rabbitmq.QueueNames;
import com.shortbridge.support.rabbitmq.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitPublishGateway implements PublishGateway {

  private final RabbitTemplate rabbitTemplate;
  private final PublishJobCommandService publishJobCommandService;

  @Override
  @Transactional
  public void enqueue(EnqueueRequest request) {
    var job = publishJobCommandService.enqueueStub(request.postTargetId(), request.postId(), request.platform());
    String routingKey = QueueNames.forPlatform(request.platform().name());
    PublishMessage message =
        new PublishMessage(
            request.postTargetId(),
            request.postId(),
            request.userId(),
            request.platform(),
            job.getAttempt(),
            IdempotencyKeys.forPostTarget(request.postId(), request.platform().name()));
    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, message);
    log.info("enqueued publish job: target={} platform={} attempt={}", request.postTargetId(), request.platform(), job.getAttempt());
  }
}
