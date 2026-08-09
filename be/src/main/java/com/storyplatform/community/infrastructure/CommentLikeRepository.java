package com.storyplatform.community.infrastructure;

import com.storyplatform.community.domain.CommentLike;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CommentLikeRepository extends CrudRepository<CommentLike, UUID> {
}
