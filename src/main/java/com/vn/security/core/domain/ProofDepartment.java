package com.vn.security.core.domain;

import com.vn.security.core.security.catalog.SecuredEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Proof / demo department entity linked to a {@link ProofOrganization} and its employees.
 *
 * <p>Renamed from {@code Department} to {@code ProofDepartment} so consumer
 * applications can keep their own {@code Department} domain class without bean
 * or URL collisions.</p>
 */
@SecuredEntity(code = "proof-department", jpqlAllowed = true)
@Entity
@Table(name = "proof_department")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ProofDepartment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 100)
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @NotNull
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 100)
    @Column(name = "cost_center", length = 100)
    private String costCenter;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private ProofOrganization organization;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<ProofEmployee> employees = new HashSet<>();

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

    public ProofOrganization getOrganization() {
        return organization;
    }

    public void setOrganization(ProofOrganization organization) {
        this.organization = organization;
    }

    public Set<ProofEmployee> getEmployees() {
        return employees;
    }

    public void setEmployees(Set<ProofEmployee> employees) {
        this.employees = employees;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProofDepartment)) {
            return false;
        }
        return id != null && id.equals(((ProofDepartment) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
