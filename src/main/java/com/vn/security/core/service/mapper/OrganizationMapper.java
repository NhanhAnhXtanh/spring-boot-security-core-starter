package com.vn.security.core.service.mapper;

import com.vn.security.core.domain.Department;
import com.vn.security.core.domain.Employee;
import com.vn.security.core.domain.Organization;
import com.vn.security.core.service.dto.DepartmentDTO;
import com.vn.security.core.service.dto.EmployeeDTO;
import com.vn.security.core.service.dto.OrganizationDTO;
import com.vn.security.core.service.dto.OrganizationDetailDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper for the entity {@link Organization} and its DTOs {@link OrganizationDTO} and {@link OrganizationDetailDTO}.
 */
@Mapper(componentModel = "spring")
public interface OrganizationMapper extends EntityMapper<OrganizationDTO, Organization> {
    /**
     * Map Organization entity to list DTO (id, code, name, ownerLogin).
     */
    @Override
    OrganizationDTO toDto(Organization organization);

    /**
     * Map Organization entity to detail DTO (includes budget and nested departments/employees).
     */
    OrganizationDetailDTO toDetailDto(Organization organization);

    /**
     * Map Department entity to DTO.
     */
    DepartmentDTO departmentToDto(Department department);

    /**
     * Map Employee entity to DTO.
     */
    EmployeeDTO employeeToDto(Employee employee);

    @Override
    @Mapping(target = "budget", ignore = true)
    @Mapping(target = "departments", ignore = true)
    Organization toEntity(OrganizationDTO organizationDTO);

    @Override
    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "budget", ignore = true)
    @Mapping(target = "departments", ignore = true)
    void partialUpdate(@MappingTarget Organization entity, OrganizationDTO dto);
}
