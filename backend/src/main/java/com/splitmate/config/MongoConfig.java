package com.splitmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * MongoDB settings. Indexes are declared on the documents with {@code @Indexed}/{@code @CompoundIndex}
 * and created at startup ({@code spring.data.mongodb.auto-index-creation: true}).
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

	/**
	 * Use the driver's java.time codecs, which store {@code LocalDate}/{@code Instant} in UTC. With the
	 * Spring Data default a {@code LocalDate} is converted through the JVM time zone, so 2026-10-12 in
	 * Berlin would be saved as 2026-10-11T22:00Z and date aggregations ({@code $dateToString},
	 * {@code $dayOfWeek}) would land on the wrong day.
	 */
	@Bean
	MongoCustomConversions mongoCustomConversions() {
		return MongoCustomConversions.create(adapter -> adapter.useNativeDriverJavaTimeCodecs());
	}

	/**
	 * Same converter Spring Boot would create, minus the {@code _class} type hint: no document here is
	 * polymorphic, so the hint is noise when the data is shown in mongosh or Compass.
	 */
	@Bean
	MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory, MongoMappingContext context,
			MongoCustomConversions conversions) {
		MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), context);
		converter.setCustomConversions(conversions);
		converter.setTypeMapper(new DefaultMongoTypeMapper(null));
		return converter;
	}

}
