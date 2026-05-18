package com.vn.security.core.service.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * A DTO for the {@link com.vn.security.core.domain.ProofOrganization} entity (list view).
 */
public class ProofOrganizationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    private String ownerLogin;

    public ProofOrganizationDTO() {
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

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProofOrganizationDTO{" +
            "id=" + id +
            ", code='" + code + '\'' +
            ", name='" + name + '\'' +
            ", ownerLogin='" + ownerLogin + '\'' +
            "}";
    }
}
