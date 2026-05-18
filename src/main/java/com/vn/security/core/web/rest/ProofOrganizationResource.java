package com.vn.security.core.web.rest;

import com.vn.security.core.domain.ProofOrganization;
import com.vn.security.core.security.data.SecureDataManager.EntityMutation;
import com.vn.security.core.security.web.SecuredEntityJsonAdapter;
import com.vn.security.core.security.web.SecuredEntityPayloadValidator;
import com.vn.security.core.service.ProofOrganizationService;
import com.vn.security.core.util.PaginationUtil;
import com.vn.security.core.web.rest.vm.SecuredEntityQueryVM;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller exposing secured CRUD endpoints for proof / demo organizations.
 *
 * <p>Path is namespaced under {@code /api/proof/} to avoid colliding with any
 * {@code /api/organizations} endpoint the consumer application may expose.</p>
 */
@Tag(
    name = "ProofOrganizations",
    description = "Secured CRUD for ProofOrganization (demo) entities. Responses are attribute-filtered by the caller's VIEW " +
    "permission via SecureEntitySerializerImpl."
)
@RestController
@RequestMapping("/api/proof/organizations")
@PreAuthorize("isAuthenticated()")
public class ProofOrganizationResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProofOrganizationResource.class);
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String LIST_FETCH_PLAN = "proof-organization-list";
    private static final String DETAIL_FETCH_PLAN = "proof-organization-detail";

    private final ProofOrganizationService proofOrganizationService;
    private final SecuredEntityJsonAdapter securedEntityJsonAdapter;
    private final SecuredEntityPayloadValidator securedEntityPayloadValidator;

    public ProofOrganizationResource(
        ProofOrganizationService proofOrganizationService,
        SecuredEntityJsonAdapter securedEntityJsonAdapter,
        SecuredEntityPayloadValidator securedEntityPayloadValidator
    ) {
        this.proofOrganizationService = proofOrganizationService;
        this.securedEntityJsonAdapter = securedEntityJsonAdapter;
        this.securedEntityPayloadValidator = securedEntityPayloadValidator;
    }

    @Operation(
        operationId = "getAllProofOrganizations",
        summary = "List proof organizations (paginated)",
        description = "Returns a paginated list of proof organizations through the @SecuredEntity pipeline. Uses fetch-plan " +
        "'proof-organization-list': fields id, code, name, ownerLogin (no nested relations). Fields may be omitted " +
        "if the caller lacks VIEW permission for that attribute."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    type = "array",
                    description = "JSON array of proof organization objects (fetch-plan: proof-organization-list)."
                )
            )
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity READ permission", content = @Content),
    })
    @GetMapping("")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getAllProofOrganizations(@ParameterObject Pageable pageable) {
        LOG.debug("REST request to get proof organizations");
        Page<ProofOrganization> page = proofOrganizationService.list(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonArrayString(page.getContent(), LIST_FETCH_PLAN));
    }

    @Operation(
        operationId = "getProofOrganization",
        summary = "Get proof organization by ID",
        description = "Returns a single proof organization through the @SecuredEntity pipeline. Uses fetch-plan " +
        "'proof-organization-detail'."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "ProofOrganization not found", content = @Content),
    })
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getProofOrganization(
        @Parameter(description = "ProofOrganization ID", required = true) @PathVariable("id") Long id
    ) {
        LOG.debug("REST request to get proof organization : {}", id);
        return proofOrganizationService
            .findOne(id)
            .map(organization ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(securedEntityJsonAdapter.toJsonString(organization, DETAIL_FETCH_PLAN))
            )
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
        operationId = "createProofOrganization",
        summary = "Create a new proof organization",
        description = "Creates a proof organization through the @SecuredEntity pipeline. Returns the created entity " +
        "serialized via fetch-plan 'proof-organization-detail'."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity CREATE permission", content = @Content),
    })
    @PostMapping("")
    @Transactional
    public ResponseEntity<String> createProofOrganization(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofOrganization attributes as JSON object",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(type = "object", example = "{\"code\":\"ORG-001\",\"name\":\"Acme Corp\",\"ownerLogin\":\"admin\"}")
            )
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to create proof organization");
        EntityMutation<ProofOrganization> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofOrganization.class);
        ProofOrganization result = proofOrganizationService.create(mutation);
        return ResponseEntity.created(URI.create("/api/proof/organizations/" + result.getId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(result, DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "updateProofOrganization",
        summary = "Update an existing proof organization",
        description = "Full update of a proof organization through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Updated",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity UPDATE permission", content = @Content),
        @ApiResponse(responseCode = "404", description = "ProofOrganization not found", content = @Content),
    })
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<String> updateProofOrganization(
        @Parameter(description = "ProofOrganization ID", required = true) @PathVariable("id") Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofOrganization attributes as JSON object",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to update proof organization : {}", id);
        EntityMutation<ProofOrganization> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofOrganization.class);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(proofOrganizationService.update(id, mutation), DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "patchProofOrganization",
        summary = "Partial update a proof organization",
        description = "PATCH partial update through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Updated",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "ProofOrganization not found", content = @Content),
    })
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @Transactional
    public ResponseEntity<String> patchProofOrganization(
        @Parameter(description = "ProofOrganization ID", required = true) @PathVariable("id") Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofOrganization attributes as JSON object",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to patch proof organization : {}", id);
        EntityMutation<ProofOrganization> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofOrganization.class);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(proofOrganizationService.patch(id, mutation), DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "queryProofOrganizations",
        summary = "Query proof organizations with filters",
        description = "Paginated query with optional filters through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "array"))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid query", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
    })
    @PostMapping("/query")
    @Transactional(readOnly = true)
    public ResponseEntity<String> queryProofOrganizations(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Query payload with optional filters, pagination, sort, and fetchPlanCode",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @Valid @RequestBody SecuredEntityQueryVM request
    ) {
        LOG.debug("REST request to query proof organizations");
        String fetchPlanCode = resolveFetchPlanCode(request.fetchPlanCode(), LIST_FETCH_PLAN);
        securedEntityPayloadValidator.validateQuery(request, ProofOrganization.class, fetchPlanCode);
        Page<ProofOrganization> page = proofOrganizationService.query(fetchPlanCode, buildPageable(request), request.filters());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonArrayString(page.getContent(), fetchPlanCode));
    }

    @Operation(
        operationId = "deleteProofOrganization",
        summary = "Delete a proof organization",
        description = "Deletes a proof organization through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity DELETE permission", content = @Content),
    })
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteProofOrganization(
        @Parameter(description = "ProofOrganization ID", required = true) @PathVariable("id") Long id
    ) {
        LOG.debug("REST request to delete proof organization : {}", id);
        proofOrganizationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveFetchPlanCode(String fetchPlanCode, String defaultFetchPlanCode) {
        return fetchPlanCode == null || fetchPlanCode.isBlank() ? defaultFetchPlanCode : fetchPlanCode;
    }

    private Pageable buildPageable(SecuredEntityQueryVM request) {
        int page = request.page() != null && request.page() >= 0 ? request.page() : DEFAULT_PAGE;
        int size = request.size() != null && request.size() > 0 ? request.size() : DEFAULT_SIZE;
        return PageRequest.of(page, size, buildSort(request.sort()));
    }

    private Sort buildSort(List<String> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return Sort.unsorted();
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String sortValue : sortValues) {
            if (sortValue == null || sortValue.isBlank()) {
                continue;
            }

            String[] parts = sortValue.split(",", 2);
            String property = parts[0].trim();
            if (property.isEmpty()) {
                continue;
            }

            Sort.Direction direction =
                parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC : Sort.Direction.ASC;
            orders.add(new Sort.Order(direction, property));
        }

        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
