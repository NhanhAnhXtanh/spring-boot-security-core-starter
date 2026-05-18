package com.vn.security.core.web.rest;

import com.vn.security.core.domain.ProofEmployee;
import com.vn.security.core.security.data.SecureDataManager.EntityMutation;
import com.vn.security.core.security.web.SecuredEntityJsonAdapter;
import com.vn.security.core.security.web.SecuredEntityPayloadValidator;
import com.vn.security.core.service.ProofEmployeeService;
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
 * REST controller exposing secured CRUD endpoints for proof / demo employees.
 *
 * <p>Path is namespaced under {@code /api/proof/} to avoid colliding with any
 * {@code /api/employees} endpoint the consumer application may expose.</p>
 */
@Tag(
    name = "ProofEmployees",
    description = "Secured CRUD for ProofEmployee (demo) entities. Responses are attribute-filtered by the caller's VIEW " +
    "permission via SecureEntitySerializerImpl."
)
@RestController
@RequestMapping("/api/proof/employees")
@PreAuthorize("isAuthenticated()")
public class ProofEmployeeResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProofEmployeeResource.class);
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String LIST_FETCH_PLAN = "proof-employee-list";
    private static final String DETAIL_FETCH_PLAN = "proof-employee-detail";

    private final ProofEmployeeService proofEmployeeService;
    private final SecuredEntityJsonAdapter securedEntityJsonAdapter;
    private final SecuredEntityPayloadValidator securedEntityPayloadValidator;

    public ProofEmployeeResource(
        ProofEmployeeService proofEmployeeService,
        SecuredEntityJsonAdapter securedEntityJsonAdapter,
        SecuredEntityPayloadValidator securedEntityPayloadValidator
    ) {
        this.proofEmployeeService = proofEmployeeService;
        this.securedEntityJsonAdapter = securedEntityJsonAdapter;
        this.securedEntityPayloadValidator = securedEntityPayloadValidator;
    }

    @Operation(
        operationId = "getAllProofEmployees",
        summary = "List proof employees (paginated)",
        description = "Returns a paginated list of proof employees through the @SecuredEntity pipeline."
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
    public ResponseEntity<String> getAllProofEmployees(@ParameterObject Pageable pageable) {
        LOG.debug("REST request to get proof employees");
        Page<ProofEmployee> page = proofEmployeeService.list(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonArrayString(page.getContent(), LIST_FETCH_PLAN));
    }

    @Operation(
        operationId = "getProofEmployee",
        summary = "Get proof employee by ID",
        description = "Returns a single proof employee through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "ProofEmployee not found", content = @Content),
    })
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getProofEmployee(
        @Parameter(description = "ProofEmployee ID", required = true) @PathVariable("id") Long id
    ) {
        LOG.debug("REST request to get proof employee : {}", id);
        return proofEmployeeService
            .findOne(id)
            .map(employee ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(securedEntityJsonAdapter.toJsonString(employee, DETAIL_FETCH_PLAN))
            )
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
        operationId = "createProofEmployee",
        summary = "Create a new proof employee",
        description = "Creates a proof employee through the @SecuredEntity pipeline."
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
    public ResponseEntity<String> createProofEmployee(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofEmployee attributes as JSON object",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    type = "object",
                    example = "{\"employeeNumber\":\"EMP-001\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"
                )
            )
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to create proof employee");
        EntityMutation<ProofEmployee> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofEmployee.class);
        ProofEmployee result = proofEmployeeService.create(mutation);
        return ResponseEntity.created(URI.create("/api/proof/employees/" + result.getId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(result, DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "updateProofEmployee",
        summary = "Update an existing proof employee",
        description = "Full update of a proof employee through the @SecuredEntity pipeline."
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
        @ApiResponse(responseCode = "404", description = "ProofEmployee not found", content = @Content),
    })
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<String> updateProofEmployee(
        @Parameter(description = "ProofEmployee ID", required = true) @PathVariable("id") Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofEmployee attributes as JSON object",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to update proof employee : {}", id);
        EntityMutation<ProofEmployee> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofEmployee.class);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(proofEmployeeService.update(id, mutation), DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "patchProofEmployee",
        summary = "Partial update a proof employee",
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
        @ApiResponse(responseCode = "404", description = "ProofEmployee not found", content = @Content),
    })
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @Transactional
    public ResponseEntity<String> patchProofEmployee(
        @Parameter(description = "ProofEmployee ID", required = true) @PathVariable("id") Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "ProofEmployee attributes as JSON object",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @RequestBody String attributesJson
    ) {
        LOG.debug("REST request to patch proof employee : {}", id);
        EntityMutation<ProofEmployee> mutation = securedEntityJsonAdapter.fromJson(attributesJson, ProofEmployee.class);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonString(proofEmployeeService.patch(id, mutation), DETAIL_FETCH_PLAN));
    }

    @Operation(
        operationId = "queryProofEmployees",
        summary = "Query proof employees with filters",
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
    public ResponseEntity<String> queryProofEmployees(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Query payload with optional filters, pagination, sort, and fetchPlanCode",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"))
        ) @Valid @RequestBody SecuredEntityQueryVM request
    ) {
        LOG.debug("REST request to query proof employees");
        String fetchPlanCode = resolveFetchPlanCode(request.fetchPlanCode(), LIST_FETCH_PLAN);
        securedEntityPayloadValidator.validateQuery(request, ProofEmployee.class, fetchPlanCode);
        Page<ProofEmployee> page = proofEmployeeService.query(fetchPlanCode, buildPageable(request), request.filters());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(securedEntityJsonAdapter.toJsonArrayString(page.getContent(), fetchPlanCode));
    }

    @Operation(
        operationId = "deleteProofEmployee",
        summary = "Delete a proof employee",
        description = "Deletes a proof employee through the @SecuredEntity pipeline."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden — missing entity DELETE permission", content = @Content),
    })
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteProofEmployee(
        @Parameter(description = "ProofEmployee ID", required = true) @PathVariable("id") Long id
    ) {
        LOG.debug("REST request to delete proof employee : {}", id);
        proofEmployeeService.delete(id);
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
