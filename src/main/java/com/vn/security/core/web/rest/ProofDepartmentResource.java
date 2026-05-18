package com.vn.security.core.web.rest;

import com.vn.security.core.domain.ProofDepartment;
import com.vn.security.core.security.data.SecureDataManager.EntityMutation;
import com.vn.security.core.security.web.SecuredEntityJsonAdapter;
import com.vn.security.core.security.web.SecuredEntityPayloadValidator;
import com.vn.security.core.service.ProofDepartmentService;
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
 * REST controller exposing secured CRUD endpoints for proof / demo departments.
 *
 * <p>Path is namespaced under {@code /api/proof/} to avoid colliding with any
 * {@code /api/departments} endpoint the consumer application may expose.</p>
 */
@Tag(
    name = "ProofDepartments",
    description = "Secured CRUD for ProofDepartment (demo) entities. Responses are attribute-filtered by the caller's VIEW " +
    "permission via SecureEntitySerializerImpl."
)
@RestController
@RequestMapping("/api/proof/departments")
@PreAuthorize("isAuthenticated()")
public class ProofDepartmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProofDepartmentResource.class);
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String LIST_FETCH_PLAN = "proof-department-list";
    private static final String DETAIL_FETCH_PLAN = "proof-department-detail";

    private final ProofDepartmentService proofDepartmentService;
    private final SecuredEntityJsonAdapter securedEntityJsonAdapter;
    private final SecuredEntityPayloadValidator securedEntityPayloadValidator;

    public ProofDepartmentResource(
        ProofDepartmentService proofDepartmentService,
        SecuredEntityJsonAdapter securedEntityJsonAdapter,
        SecuredEntityPayloadValidator securedEntityPayloadValidator
    ) {
        this.proofDepartmentService = proofDepartmentService;
        this.securedEntityJsonAdapter = securedEntityJsonAdapter;
        this.securedEntityPayloadValidator = securedEntityPayloadValidator;
    }

    @Operation(
        operationId = "getAllProofDepartments",
        summary = "List proof departments (paginated)",
        description = "Returns a paginated list of proof departments through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "array"))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity READ permission", content = @Content),
    })
    @GetMapping("")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getAllProofDepartments(@ParameterObject Pageable pageable) {
        LOG.debug("REST request to get proof departments");
        Page<ProofDepartment> page = proofDepartmentService.list(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonArrayString(page.getContent(), LIST_FETCH_PLAN));
    }

    @Operation(
        operationId = "getProofDepartment",
        summary = "Get proof department by ID",
        description = "Returns a single proof department through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "ProofDepartment not found", content = @Content),
    })
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getProofDepartment(
        @Parameter(description = "ProofDepartment ID", required = true) @PathVariable("id") Long id
    ) {
        LOG.debug("REST request to get proof department : {}", id);
        return proofDepartmentService
            .findOne(id)
            .map(department ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(securedEntityJsonAdapter.toJsonString(department, DETAIL_FETCH_PLAN))
            )
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
        operationId = "createProofDepartment",
        summary = "Create a new proof department",
        description = "Creates a proof department through the @SecuredEntity pipeline."
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
    public ResponseEntity<String> createProofDepartment(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofDepartment attributes as JSON object",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(type = "object", example = "{\"code\":\"DEP-001\",\"name\":\"Engineering\",\"costCenter\":\"CC-100\"}")
            )
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to create proof department");
        EntityMutation<ProofDepartment> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofDepartment.class);
        ProofDepartment result = proofDepartmentService.create(mutation);
        return ResponseEntity.created(URI.create("/api/proof/departments/" + result.getId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(result, DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "updateProofDepartment",
        summary = "Update an existing proof department",
        description = "Full update of a proof department through the @SecuredEntity pipeline."
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
        @ApiResponse(responseCode = "404", description = "ProofDepartment not found", content = @Content),
    })
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<String> updateProofDepartment(
        @Parameter(description = "ProofDepartment ID", required = true) @PathVariable("id") Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofDepartment attributes as JSON object",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to update proof department : {}", id);
        EntityMutation<ProofDepartment> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofDepartment.class);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(proofDepartmentService.update(id, mutation), DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "patchProofDepartment",
        summary = "Partial update a proof department",
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
        @ApiResponse(responseCode = "404", description = "ProofDepartment not found", content = @Content),
    })
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @Transactional
    public ResponseEntity<String> patchProofDepartment(
        @Parameter(description = "ProofDepartment ID", required = true) @PathVariable("id") Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofDepartment attributes as JSON object",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to patch proof department : {}", id);
        EntityMutation<ProofDepartment> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofDepartment.class);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(proofDepartmentService.patch(id, mutation), DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "queryProofDepartments",
        summary = "Query proof departments with filters",
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
    public ResponseEntity<String> queryProofDepartments(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Query payload with optional filters, pagination, sort, and fetchPlanCode",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @Valid @RequestBody SecuredEntityQueryVM request
    ) {
        LOG.debug("REST request to query proof departments");
        String fetchPlanCode = resolveFetchPlanCode(request.fetchPlanCode(), LIST_FETCH_PLAN);
        securedEntityPayloadValidator.validateQuery(request, ProofDepartment.class, fetchPlanCode);
        Page<ProofDepartment> page = proofDepartmentService.query(fetchPlanCode, buildPageable(request), request.filters());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonArrayString(page.getContent(), fetchPlanCode));
    }

    @Operation(
        operationId = "deleteProofDepartment",
        summary = "Delete a proof department",
        description = "Deletes a proof department through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity DELETE permission", content = @Content),
    })
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteProofDepartment(
        @Parameter(description = "ProofDepartment ID", required = true) @PathVariable("id") Long id
    ) {
        LOG.debug("REST request to delete proof department : {}", id);
        proofDepartmentService.delete(id);
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
