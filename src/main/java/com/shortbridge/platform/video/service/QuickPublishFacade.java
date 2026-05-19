package com.shortbridge.platform.video.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.post.domain.PublishMode;
import com.shortbridge.platform.post.dto.command.CreatePostCommand;
import com.shortbridge.platform.post.dto.response.PostResponse;
import com.shortbridge.platform.post.service.PostFacade;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.domain.SocialAccountStatus;
import com.shortbridge.platform.socialaccount.service.SocialAccountQueryService;
import com.shortbridge.platform.video.dto.response.VideoResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuickPublishFacade {

  private final VideoFacade videoFacade;
  private final PostFacade postFacade;
  private final SocialAccountQueryService socialAccountQueryService;

  @Transactional
  public PostResponse uploadAndPublishAll(UUID userId, MultipartFile file, String title) {
    List<Platform> connectedPlatforms =
        socialAccountQueryService.list(userId).stream()
            .filter(sa -> sa.getStatus() == SocialAccountStatus.CONNECTED)
            .map(SocialAccount::getPlatform)
            .distinct()
            .collect(Collectors.toList());

    if (connectedPlatforms.isEmpty()) {
      throw ShortBridgeException.of(
          ErrorCode.INVALID_REQUEST,
          "연결된 플랫폼이 0개입니다. /social-accounts 에서 먼저 연결하세요.");
    }

    VideoResponse video = videoFacade.upload(userId, file);
    String resolvedTitle =
        (title == null || title.isBlank()) ? defaultTitle(video.originalFileName()) : title;

    CreatePostCommand command =
        new CreatePostCommand(
            userId,
            video.id(),
            resolvedTitle,
            null,
            List.of(),
            PublishMode.NOW,
            null,
            connectedPlatforms);

    PostResponse post = postFacade.create(command);
    log.info(
        "quick publish: userId={} videoId={} postId={} platforms={}",
        userId,
        video.id(),
        post.id(),
        connectedPlatforms);
    return post;
  }

  private String defaultTitle(String filename) {
    if (filename == null) return "ShortBridge upload";
    int dot = filename.lastIndexOf('.');
    return dot < 0 ? filename : filename.substring(0, dot);
  }
}
