package com.studytrack.api.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code app.catalog.*}.
 *
 * @param seedOnStartup when true and the catalog is empty, the CSV files in {@code classpath:catalog/} are
 *                      imported at startup
 */
@ConfigurationProperties("app.catalog")
public record CatalogProperties(@DefaultValue("true") boolean seedOnStartup) {
}
