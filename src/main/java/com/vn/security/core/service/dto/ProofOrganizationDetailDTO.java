package com.vn.security.core.service.dto;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * A DTO for the {@link com.vn.security.core.domain.ProofOrganization} entity (detail view).
 */
public class ProofOrganizationDetailDTO extends ProofOrganizationDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    private BigDecimal budget;

    private Set<ProofDepartmentDTO> departments = new HashSet<>();

    public ProofOrganizationDetailDTO() {
        // Empty constructor needed for Jackson.
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public Set<ProofDepartmentDTO> getDepartments() {
        return departments;
    }

    public void setDepartments(Set<ProofDepartmentDTO> departments) {
        this.departments = departments;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProofOrganizationDetailDTO{" +
            "id=" + getId() +
            ", code='" + getCode() + '\'' +
            ", name='" + getName() + '\'' +
            ", ownerLogin='" + getOwnerLogin() + '\'' +
            ", budget=" + budget +
            ", departments=" + departments +
            "}";
    }
}
