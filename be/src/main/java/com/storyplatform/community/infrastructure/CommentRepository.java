package com.storyplatform.community.infrastructure;

import com.storyplatform.community.domain.Comment;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CommentRepository extends CrudRepository<Comment, UUID> {
}
