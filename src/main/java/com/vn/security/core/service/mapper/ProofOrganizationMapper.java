package com.vn.security.core.service.mapper;

import com.vn.security.core.domain.ProofDepartment;
import com.vn.security.core.domain.ProofEmployee;
import com.vn.security.core.domain.ProofOrganization;
import com.vn.security.core.service.dto.ProofDepartmentDTO;
import com.vn.security.core.service.dto.ProofEmployeeDTO;
import com.vn.security.core.service.dto.ProofOrganizationDTO;
import com.vn.security.core.service.dto.ProofOrganizationDetailDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper for the entity {@link ProofOrganization} and its DTOs
 * {@link ProofOrganizationDTO} and {@link ProofOrganizationDetailDTO}.
 */
@Mapper(componentModel = "spring")
public interface ProofOrganizationMapper extends EntityMapper<ProofOrganizationDTO, ProofOrganization> {
    /**
     * Map ProofOrganization entity to list DTO (id, code, name, ownerLogin).
     */
    @Override
    ProofOrganizationDTO toDto(ProofOrganization organization);

    /**
     * Map ProofOrganization entity to detail DTO (includes budget and nested departments/employees).
     */
    ProofOrganizationDetailDTO toDetailDto(ProofOrganization organization);

    /**
     * Map ProofDepartment entity to DTO.
     */
    ProofDepartmentDTO departmentToDto(ProofDepartment department);

    /**
     * Map ProofEmployee entity to DTO.
     */
    ProofEmployeeDTO employeeToDto(ProofEmployee employee);

    @Override
    @Mapping(target = "budget", ignore = true)
    @Mapping(target = "departments", ignore = true)
    ProofOrganization toEntity(ProofOrganizationDTO organizationDTO);

    @Override
    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "budget", ignore = true)
    @Mapping(target = "departments", ignore = true)
    void partialUpdate(@MappingTarget ProofOrganization entity, ProofOrganizationDTO dto);
}
