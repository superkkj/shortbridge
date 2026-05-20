package com.shortbridge.platform.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostStatus.recomputeFrom — Post 상태 도메인 메서드")
class PostStatusTest {

  @Test
  void empty_returnsEmpty() {
    assertThat(PostStatus.recomputeFrom(List.of())).isEmpty();
    assertThat(PostStatus.recomputeFrom(null)).isEmpty();
  }

  @Test
  void anyNonTerminal_returnsProcessing() {
    assertThat(PostStatus.recomputeFrom(List.of(PostTargetStatus.READY)))
        .contains(PostStatus.PROCESSING);
    assertThat(PostStatus.recomputeFrom(List.of(PostTargetStatus.PUBLISHED, PostTargetStatus.QUEUED)))
        .contains(PostStatus.PROCESSING);
    assertThat(
            PostStatus.recomputeFrom(
                List.of(PostTargetStatus.UPLOADING, PostTargetStatus.FAILED_PERMANENT)))
        .contains(PostStatus.PROCESSING);
  }

  @Test
  void allTerminal_mixedSuccessAndFail_returnsPartialFailed() {
    assertThat(
            PostStatus.recomputeFrom(
                List.of(PostTargetStatus.PUBLISHED, PostTargetStatus.FAILED_PERMANENT)))
        .contains(PostStatus.PARTIAL_FAILED);
  }

  @Test
  void allTerminal_allPublished_returnsPublished() {
    assertThat(
            PostStatus.recomputeFrom(
                List.of(PostTargetStatus.PUBLISHED, PostTargetStatus.PUBLISHED)))
        .contains(PostStatus.PUBLISHED);
  }

  @Test
  void allTerminal_someCanceledSomePublished_returnsPublished() {
    assertThat(
            PostStatus.recomputeFrom(
                List.of(PostTargetStatus.PUBLISHED, PostTargetStatus.CANCELED)))
        .contains(PostStatus.PUBLISHED);
  }

  @Test
  void allTerminal_noSuccess_returnsFailed() {
    assertThat(
            PostStatus.recomputeFrom(
                List.of(PostTargetStatus.FAILED_PERMANENT, PostTargetStatus.CANCELED)))
        .contains(PostStatus.FAILED);
    assertThat(PostStatus.recomputeFrom(List.of(PostTargetStatus.CANCELED)))
        .contains(PostStatus.FAILED);
  }

  @Test
  void singlePublished_isTerminalAndSucceeds() {
    assertThat(PostStatus.recomputeFrom(List.of(PostTargetStatus.PUBLISHED)))
        .contains(PostStatus.PUBLISHED);
  }
}
