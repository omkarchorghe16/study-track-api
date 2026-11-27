package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogDtos.CatalogImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@Tag(name = "Catalog admin", description = "Changing the shared catalog (admin only)")
public class CatalogAdminController {
    private final CatalogAdminService catalogAdminService;

    public CatalogAdminController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import tracks, subjects and topics from a CSV",
            description = "Columns: track, subject, optional topic_path, and optional question, answer, difficulty (a starter "
                    + "question under that topic). Adds what is missing; existing rows and "
                    + "user data are never changed or deleted. Rows without a track or subject are reported, not imported.")
    @ApiResponse(responseCode = "400", description = "Not a readable CSV (for example an unclosed quote)")
    @ApiResponse(responseCode = "403", description = "The current user is not an admin")
    public CatalogImportResponse importCatalogCsv(@RequestPart("file") MultipartFile file) throws IOException {
        return catalogAdminService.importCatalogCsv(file.getBytes());
    }
}
