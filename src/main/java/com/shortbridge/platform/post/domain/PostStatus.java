package com.shortbridge.platform.post.domain;

import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import java.util.List;
import java.util.Optional;

public enum PostStatus {
  DRAFT,
  QUEUED,
  PROCESSING,
  PUBLISHED,
  PARTIAL_FAILED,
  FAILED,
  CANCELED;

  /**
   * Post 의 다음 status 를 PostTarget 들의 status 모음으로부터 계산한다.
   *
   * <ul>
   *   <li>아직 terminal 이 아닌 target 이 하나라도 있으면 {@code PROCESSING}</li>
   *   <li>전부 terminal + 성공/실패가 섞여 있으면 {@code PARTIAL_FAILED}</li>
   *   <li>전부 terminal + 성공만 있으면 {@code PUBLISHED}</li>
   *   <li>전부 terminal + 성공이 없으면 {@code FAILED}</li>
   * </ul>
   *
   * 빈 입력은 status 갱신 자체를 하지 않는다는 의미로 {@link Optional#empty()} 반환.
   */
  public static Optional<PostStatus> recomputeFrom(List<PostTargetStatus> targetStatuses) {
    if (targetStatuses == null || targetStatuses.isEmpty()) {
      return Optional.empty();
    }
    boolean allTerminal = targetStatuses.stream().allMatch(PostTargetStatus::isTerminal);
    if (!allTerminal) {
      return Optional.of(PROCESSING);
    }
    boolean anySuccess = targetStatuses.stream().anyMatch(PostStatus::isSuccess);
    boolean anyFail = targetStatuses.stream().anyMatch(PostStatus::isFailure);
    if (anySuccess && anyFail) return Optional.of(PARTIAL_FAILED);
    if (anySuccess) return Optional.of(PUBLISHED);
    return Optional.of(FAILED);
  }

  private static boolean isSuccess(PostTargetStatus status) {
    return status == PostTargetStatus.PUBLISHED || status == PostTargetStatus.PRIVATE_LIMITED;
  }

  private static boolean isFailure(PostTargetStatus status) {
    return status == PostTargetStatus.FAILED_PERMANENT
        || status == PostTargetStatus.BLOCKED_BY_CAPABILITY
        || status == PostTargetStatus.RECONNECT_REQUIRED;
  }
}
