package com.splitmate.config;

import java.io.IOException;
import java.util.List;

import com.splitmate.common.security.CurrentUserIdArgumentResolver;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Web MVC setup: the {@code @CurrentUserId} resolver, and serving the built React app (single-jar build)
 * with client-side routing support. A path that is not a real file and does not start with
 * {@code api/} is answered with {@code index.html}, so deep links like /app/splits/123 work on refresh.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private static final String STATIC_LOCATION = "classpath:/static/";

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentUserIdArgumentResolver());
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC_LOCATION)
			.resourceChain(true)
			.addResolver(new SpaFallbackResolver());
	}

	static class SpaFallbackResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource requested = location.createRelative(resourcePath);
			if (requested.exists() && requested.isReadable()) {
				return requested;
			}
			if (resourcePath.startsWith("api/") || resourcePath.equals("api") || resourcePath.contains(".")) {
				return null; // real 404 for unknown API calls and missing assets
			}
			Resource index = new ClassPathResource("static/index.html");
			return index.exists() ? index : null;
		}

	}

}
