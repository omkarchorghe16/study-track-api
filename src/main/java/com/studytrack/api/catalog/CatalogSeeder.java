package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogDtos.CatalogImportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Fills an empty catalog from the CSV files in {@code src/main/resources/catalog} (imported in file name order).
 * Runs once: when any track exists the catalog is left alone, so an admin's later changes are never undone.
 */
@Component
public class CatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);
    private static final String SEED_FILES_PATTERN = "classpath:catalog/*.csv";

    private final CatalogProperties catalogProperties;
    private final CatalogTrackRepository catalogTrackRepository;
    private final CatalogImportService catalogImportService;

    public CatalogSeeder(CatalogProperties catalogProperties, CatalogTrackRepository catalogTrackRepository,
                         CatalogImportService catalogImportService) {
        this.catalogProperties = catalogProperties;
        this.catalogTrackRepository = catalogTrackRepository;
        this.catalogImportService = catalogImportService;
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        if (!catalogProperties.seedOnStartup() || catalogTrackRepository.count() > 0) {
            return;
        }
        List<Resource> seedFiles = Arrays.stream(new PathMatchingResourcePatternResolver().getResources(SEED_FILES_PATTERN))
                .sorted(Comparator.comparing(Resource::getFilename))
                .toList();
        for (Resource seedFile : seedFiles) {
            CatalogImportResponse result = catalogImportService.importCatalogCsv(seedFile.getContentAsByteArray());
            log.info("Seeded catalog from {}: {} tracks, {} subjects, {} topics, {} rows rejected",
                    seedFile.getFilename(), result.tracksAdded(), result.subjectsAdded(), result.topicsAdded(),
                    result.rejectedRowCount());
        }
    }
}
