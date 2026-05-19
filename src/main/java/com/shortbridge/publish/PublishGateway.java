package com.shortbridge.publish;

import com.shortbridge.publish.dto.EnqueueRequest;

public interface PublishGateway {

  void enqueue(EnqueueRequest request);
}
