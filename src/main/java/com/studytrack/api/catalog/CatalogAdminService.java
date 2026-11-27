package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogDtos.CatalogImportResponse;
import com.studytrack.api.user.CurrentUser;
import org.springframework.stereotype.Service;

/** Catalog changes that only an admin may make. */
@Service
public class CatalogAdminService {
    private final CatalogImportService catalogImportService;
    private final CurrentUser currentUser;

    public CatalogAdminService(CatalogImportService catalogImportService, CurrentUser currentUser) {
        this.catalogImportService = catalogImportService;
        this.currentUser = currentUser;
    }

    public CatalogImportResponse importCatalogCsv(byte[] content) {
        currentUser.requireAdmin();
        return catalogImportService.importCatalogCsv(content);
    }
}
