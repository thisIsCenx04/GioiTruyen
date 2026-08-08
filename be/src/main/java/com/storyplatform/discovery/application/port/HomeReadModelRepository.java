package com.storyplatform.discovery.application.port;

import com.storyplatform.discovery.application.HomeReadModel;

import java.util.Optional;

public interface HomeReadModelRepository {

    Optional<HomeReadModel> find(String locale);

    void replace(HomeReadModel model);
}
