package com.shortbridge.connect.connector;

import com.shortbridge.platform.socialaccount.domain.Platform;

public interface SocialConnector {

  Platform platform();

  String authorizationUrl(AuthorizationContext context);

  ConnectionResult handleCallback(CallbackContext context);
}
