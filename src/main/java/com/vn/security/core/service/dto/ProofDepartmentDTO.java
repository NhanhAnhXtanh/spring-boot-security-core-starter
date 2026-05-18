package com.vn.security.core.service.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * A DTO for the {@link com.vn.security.core.domain.ProofDepartment} entity.
 */
public class ProofDepartmentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    private String costCenter;

    private Set<ProofEmployeeDTO> employees = new HashSet<>();

    public ProofDepartmentDTO() {
        // Empty constructor needed for Jackson.
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }

    public Set<ProofEmployeeDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(Set<ProofEmployeeDTO> employees) {
        this.employees = employees;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProofDepartmentDTO{" +
            "id=" + id +
            ", code='" + code + '\'' +
            ", name='" + name + '\'' +
            ", costCenter='" + costCenter + '\'' +
            ", employees=" + employees +
            "}";
    }
}
