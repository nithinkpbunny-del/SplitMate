package com.splitmate.config;

import java.util.Map;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import com.splitmate.common.ErrorResponse;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

/**
 * OpenAPI description for Swagger UI. Only active when springdoc is enabled (the dev and demo profiles);
 * with the prod profile none of this exists and {@code /swagger-ui.html} answers 404.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

	private static final String DESCRIPTION = """
			The SplitMate API: split shared expenses, calculate who owes whom, and report on the spending.

			### Trying it out in this page
			1. Call **GET /api/auth/csrf** once (Try it out, Execute). This sets the `XSRF-TOKEN` cookie; Swagger UI then \
			sends it back in the `X-XSRF-TOKEN` header on every write automatically.
			2. Call **POST /api/auth/login** with a demo account, for example \
			`{"email": "aarav@demo.com", "password": "Demo@1234"}`. The login cookie (`SM_TOKEN`) is stored by your browser \
			and sent with every following request, so everything below now works. (Run the backend with the `demo` profile \
			for these accounts.)
			3. Alternatively use **Authorize** with a JWT as a Bearer token (copy the `SM_TOKEN` cookie value from a \
			`curl -i` login). Bearer requests need no CSRF header.

			### Conventions
			- **Money is an integer count of cents** in every response (`amountMinor: 8640` is 86.40). Requests send amounts \
			as strings (`"86,40"`, `"86.40"`, `"1.234,50"`).
			- Ids are 24-character hex ObjectIds. A split you are not a member of answers **404**, as if it did not exist.
			- Errors share one shape: `{timestamp, status, error, message, path, fieldErrors[]}`.

			The full written reference with real examples is in `docs/api.md`.
			""";

	@Bean
	OpenAPI splitMateOpenApi() {
		return new OpenAPI()
			.info(new Info().title("SplitMate API").version("1.0.0").description(DESCRIPTION))
			.components(new Components().addSecuritySchemes("bearerAuth",
					new SecurityScheme().type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Optional. The browser login cookie works too, see the description above.")))
			.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
	}

	/** Registers the shared error schema so every operation can point at it. */
	@Bean
	OpenApiCustomizer errorSchemaCustomizer() {
		return openApi -> {
			Map<String, Schema> schemas = ModelConverters.getInstance().readAll(ErrorResponse.class);
			schemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
		};
	}

	/**
	 * Fixes up responses: handlers returning {@code ResponseEntity<Void>} are 204, and every operation lists
	 * the common error responses that apply to it.
	 */
	@Bean
	OperationCustomizer responseCustomizer() {
		return (Operation operation, HandlerMethod method) -> {
			ApiResponses responses = operation.getResponses();
			if (responses == null) {
				responses = new ApiResponses();
				operation.setResponses(responses);
			}
			boolean noContent = ResponseEntity.class.equals(method.getMethod().getReturnType())
					&& method.getMethod().getGenericReturnType().getTypeName().endsWith("<java.lang.Void>");
			if (noContent && responses.containsKey("200")) {
				responses.remove("200");
				responses.addApiResponse("204", new ApiResponse().description("No content"));
			}
			// an explicit 201 replaces the default 200
			if (responses.containsKey("201")) {
				responses.remove("200");
			}
			boolean secured = operation.getSecurity() == null || !operation.getSecurity().isEmpty();
			boolean hasId = operation.getParameters() != null && operation.getParameters().stream().anyMatch(p -> "path".equals(p.getIn()));
			boolean write = !"GET".equalsIgnoreCase(httpMethod(method));
			if (write || operation.getRequestBody() != null) {
				responses.addApiResponse("400", error("Validation failed or the request is not allowed as sent"));
			}
			if (secured) {
				responses.addApiResponse("401", error("Not logged in, or the session has expired"));
				if (write) {
					responses.addApiResponse("403", error("Not allowed (owner-only action) or the CSRF token is missing"));
				}
			}
			if (hasId) {
				responses.addApiResponse("404", error("Not found, or you are not a member of that split"));
			}
			return operation;
		};
	}

	private static String httpMethod(HandlerMethod method) {
		for (var a : method.getMethod().getAnnotations()) {
			String name = a.annotationType().getSimpleName();
			if (name.endsWith("Mapping") && !name.equals("RequestMapping")) {
				return name.replace("Mapping", "").toUpperCase();
			}
		}
		return "GET";
	}

	private static ApiResponse error(String description) {
		return new ApiResponse().description(description)
			.content(new Content().addMediaType("application/json",
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
	}

}
