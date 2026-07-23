package com.storyplatform.publishing.architecturefixture;

import com.storyplatform.catalog.architecturefixture.CatalogFixture;

public final class InvalidPublishingFixture {

	private final CatalogFixture forbiddenDependency;

	public InvalidPublishingFixture(CatalogFixture forbiddenDependency) {
		this.forbiddenDependency = forbiddenDependency;
	}

	public CatalogFixture forbiddenDependency() {
		return forbiddenDependency;
	}
}
