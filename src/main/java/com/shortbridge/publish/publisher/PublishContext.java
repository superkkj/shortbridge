package com.shortbridge.publish.publisher;

import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.video.domain.Video;
import java.util.List;

public record PublishContext(PostTarget target, SocialAccount socialAccount, Video video, List<String> hashtags) {}
