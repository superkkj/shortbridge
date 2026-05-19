package com.shortbridge.connect.connector;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.socialaccount.domain.Platform;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConnectorRegistry {

  private final Map<Platform, SocialConnector> connectors;

  public ConnectorRegistry(List<SocialConnector> connectors) {
    this.connectors = connectors.stream().collect(Collectors.toMap(SocialConnector::platform, Function.identity()));
  }

  public SocialConnector get(Platform platform) {
    SocialConnector c = connectors.get(platform);
    if (c == null) throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "no connector for " + platform);
    return c;
  }

  public boolean has(Platform platform) {
    return connectors.containsKey(platform);
  }
}
