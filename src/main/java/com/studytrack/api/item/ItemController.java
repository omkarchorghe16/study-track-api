package com.studytrack.api.item;

import com.studytrack.api.item.ItemDtos.ItemPageResponse;
import com.studytrack.api.item.ItemDtos.ItemRequest;
import com.studytrack.api.item.ItemDtos.ItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Study item routes; a topic's items are listed under the topic, everything else under {@code /items}. */
@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Items", description = "Questions, notes, examples and diagrams inside a topic")
public class ItemController {
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/topics/{topicId}/items")
    @Operation(summary = "List a topic's items", description = "One page of the topic's own items (not its subtopics'), by sort order. The status filter arrives with progress tracking.")
    @ApiResponse(responseCode = "404", description = "No such topic for the current user")
    public ItemPageResponse listTopicItems(
            @PathVariable Long topicId,
            @Parameter(description = "Only items with this difficulty") @RequestParam(required = false) Difficulty difficulty,
            @Parameter(description = "Only items with this tag (case-insensitive)") @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return itemService.listTopicItems(topicId, difficulty, tag, page, size);
    }

    @GetMapping("/items/{itemId}")
    @Operation(summary = "Get an item", description = "Includes the ids of the previous and next item in the same topic.")
    public ItemResponse getItem(@PathVariable Long itemId) {
        return itemService.getItem(itemId);
    }

    @PostMapping("/items")
    @Operation(summary = "Create an item")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemRequest request) {
        ItemResponse created = itemService.createItem(request);
        return ResponseEntity.created(URI.create("/api/v1/items/" + created.id())).body(created);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Replace an item", description = "A different topicId moves the item to that topic.")
    public ItemResponse replaceItem(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request) {
        return itemService.replaceItem(itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Delete an item")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> deleteItem(@PathVariable Long itemId) {
        itemService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
